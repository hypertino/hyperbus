package com.hypertino.hyperbus.transport

import java.io.StringReader

import com.hypertino.hyperbus.model.{Body, Message, Request, RequestBase, ResponseBase}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.util.ConfigUtils._
import com.hypertino.hyperbus.util.{FuzzyIndex, FuzzyIndexItemMetaInfo, FuzzyMatcher}
import com.typesafe.config.Config
import monix.eval.Task
import monix.execution.Ack.Stop
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.ConcurrentSubject
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.util.{Random, Success}

// todo: log messages?
class InprocTransport(serialize: Boolean = false)
                     (implicit val scheduler: Scheduler) extends ClientTransport with ServerTransport {

  def this(config: Config) = this(
    serialize = config.getOptionBoolean("serialize").getOrElse(false)
  )(
    monix.execution.Scheduler.Implicits.global // todo: configurable ExecutionContext like in akka?
  )

  protected val commandSubscriptions = new FuzzyIndex[InprocCommandHyperbusObservable]
  protected val eventSubscriptions = new FuzzyIndex[InprocEventHyperbusObservable]
  protected val subscriptions = TrieMap[InprocObservable[_], Boolean]()
  protected val log: Logger = LoggerFactory.getLogger(this.getClass)

  // todo: refactor this method, it's awful
  protected def _ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer, isPublish: Boolean): Task[_] = {
    // todo: handle serialization exceptions

    val serialized = serialize(message)
    val resultTask: Option[Task[ResponseBase]] = getRandom(commandSubscriptions
      .lookupAll(message)).map { subscription ⇒

      val request: RequestBase = serialized.map { reader ⇒
        MessageReader.read(reader, subscription.inputDeserializer)
      } getOrElse {
        message
      }

      val p = Promise[ResponseBase]
      val tResult = Task.fromFuture(p.future)
      val tPublish = subscription.publish(CommandEvent(request, p))
      val t = Task.zip2(tResult, tPublish).map(_._1)
      if (serialize) {
        t.map { result ⇒
          MessageReader.read(new StringReader(result.serializeToString), responseDeserializer)
        } onErrorHandleWith {
          case r: ResponseBase ⇒
            Task.now(MessageReader.read(new StringReader(r.serializeToString), responseDeserializer))
        }
      }
      else {
        t
      }
    }

    if (!isPublish && resultTask.isEmpty) {
      Task.raiseError {
        new NoTransportRouteException(s"${message.headers.hri.serviceAddress} is not found. Headers: ${message.headers}")
      }
    }
    else {
      val publishTasks = eventSubscriptions
        .lookupAll(message)
        .groupBy(_.group)
        .map { subscriptions ⇒
          val subscription = getRandom(subscriptions._2).get

          val request: RequestBase = serialized.map { reader ⇒
            MessageReader.read(reader, subscription.inputDeserializer)
          } getOrElse {
            message
          }

          subscription.publish(request)
        }.toSeq

      if (isPublish) {
        Task.zipList(publishTasks: _*).map { _ ⇒
          new PublishResult {
            def sent = Some(true)
            def offset = None
            override def toString = s"PublishResult(sent=Some(true),offset=None)"
          }
        }
      } else {
        resultTask.get
      }
    }
  }

  override def ask(message: RequestBase, outputDeserializer: ResponseBaseDeserializer): Task[ResponseBase] = {
    _ask(message, outputDeserializer, isPublish = false).asInstanceOf[Task[ResponseBase]]
  }

  override def publish(message: RequestBase): Task[PublishResult] = {
    _ask(message, null, isPublish = true).asInstanceOf[Task[PublishResult]]
  }

  def commands[REQ <: RequestBase](matcher: RequestMatcher,
                                     inputDeserializer: RequestDeserializer[REQ]): Observable[CommandEvent[REQ]] = {

    new InprocCommandHyperbusObservable(matcher, inputDeserializer)
      .observable
      .asInstanceOf[Observable[CommandEvent[REQ]]]
  }

  def events[REQ <: RequestBase](matcher: RequestMatcher,
                                   groupName: String,
                                   inputDeserializer: RequestDeserializer[REQ]): Observable[REQ] = {

    new InprocEventHyperbusObservable(matcher, groupName, inputDeserializer)
      .observable
      .asInstanceOf[Observable[REQ]]
  }

  private [transport] def off(subscription: InprocObservable[_]): Future[Unit] = {
    subscription match {
      case i: InprocCommandHyperbusObservable ⇒
        Future {
          commandSubscriptions.remove(i)
        }

      case i: InprocEventHyperbusObservable ⇒
        Future {
          eventSubscriptions.remove(i)
        }

      case other ⇒
        Future.failed {
          new ClassCastException(s"InprocHyperbusSubscription expected instead of ${other.getClass}")
        }
    }
  }

  override def shutdown(duration: FiniteDuration): Task[Boolean] = {
    eventSubscriptions.clear()
    commandSubscriptions.clear()
    subscriptions.foreach(_._1.off())
    Task.now(true)
  }

  private def serialize(message: Message[_,_]): Option[StringReader] = {
    if (serialize) {
      Some(new StringReader(message.serializeToString))
    }
    else {
      None
    }
  }

  private val random = new Random()
  def getRandom[T](seq: Seq[T]): Option[T] = {
    val size = seq.size
    if (size > 1)
      Some(seq(random.nextInt(size)))
    else
      seq.headOption
  }

  protected abstract class InprocObservable[T] extends FuzzyMatcher {
    // FyzzyIndex properties
    def requestMatcher: RequestMatcher
    override def indexProperties: Seq[FuzzyIndexItemMetaInfo] = requestMatcher.indexProperties
    override def matches(other: Any): Boolean = requestMatcher.matches(other)

    // Subject properties
    protected val subject: ConcurrentSubject[T,T] = ConcurrentSubject.publishToOne[T]
    def off(): Unit = {
      remove()
      subject.onComplete()
    }

    def publish(t: T): Task[Ack] = {
      Task.fromFuture(subject.onNext(t).andThen {
        case Success(Stop) ⇒ remove()
      })
    }

    val observable: Observable[T] = (subscriber: Subscriber[T]) => {
      val original: Cancelable = subject.unsafeSubscribeFn(subscriber)
      add()
      () => {
        off()
        original.cancel()
      }
    }

    protected def remove(): Unit
    protected def add(): Unit
  }

  protected class InprocCommandHyperbusObservable(val requestMatcher: RequestMatcher,
                                                  val inputDeserializer: RequestDeserializer[RequestBase])
    extends InprocObservable[CommandEvent[RequestBase]] {
    override def remove(): Unit = {
      commandSubscriptions.remove(this)
      subscriptions -= this
    }
    override def add(): Unit = {
      commandSubscriptions.add(this)
      subscriptions -= this
    }
  }

  protected class InprocEventHyperbusObservable(val requestMatcher: RequestMatcher,
                                                val group: String,
                                                val inputDeserializer: RequestDeserializer[RequestBase])
    extends InprocObservable[RequestBase] {
    override def remove(): Unit = {
      eventSubscriptions.remove(this)
      subscriptions += this → false
    }
    override def add(): Unit = {
      eventSubscriptions.add(this)
      subscriptions += this → false
    }
  }
}
