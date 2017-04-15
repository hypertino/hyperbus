package com.hypertino.hyperbus.transport

import java.io.StringReader

import com.hypertino.hyperbus.model.{Message, RequestBase, ResponseBase}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.util.ConfigUtils._
import com.hypertino.hyperbus.util.{FuzzyIndex, HyperbusSubscription, SchedulerInjector}
import com.typesafe.config.Config
import monix.eval.{Callback, Task}
import monix.execution.{Cancelable, Scheduler}
import monix.reactive.Observable
import org.slf4j.{Logger, LoggerFactory}
import scaldi.Injector

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

// todo: log messages?
class InprocTransport(serialize: Boolean = false)
                     (implicit val scheduler: Scheduler) extends ClientTransport with ServerTransport {

  def this(config: Config, inj: Injector) = this(
    serialize = config.getOptionBoolean("serialize").getOrElse(false)
  )(
    SchedulerInjector(config.getOptionString("scheduler"))(inj)
  )

  protected val commandSubscriptions = new FuzzyIndex[CommandHyperbusSubscription]
  protected val eventSubscriptions = new FuzzyIndex[EventHyperbusSubscription]
  protected val subscriptions = TrieMap[HyperbusSubscription[_], Boolean]()
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

      val t = Task.create[ResponseBase]{ (_, callback) ⇒
        val command = CommandEvent(request, callback)
        subscription.publish(command).runAsync
      }

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
        }.toSeq ++ resultTask

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

    new CommandHyperbusSubscription(matcher, inputDeserializer)
      .observable
      .asInstanceOf[Observable[CommandEvent[REQ]]]
  }

  def events[REQ <: RequestBase](matcher: RequestMatcher,
                                   groupName: String,
                                   inputDeserializer: RequestDeserializer[REQ]): Observable[REQ] = {

    new EventHyperbusSubscription(matcher, groupName, inputDeserializer)
      .observable
      .asInstanceOf[Observable[REQ]]
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



  protected class CommandHyperbusSubscription(val requestMatcher: RequestMatcher,
                                              val inputDeserializer: RequestDeserializer[RequestBase])
    extends HyperbusSubscription[CommandEvent[RequestBase]] {
    override def remove(): Unit = {
      commandSubscriptions.remove(this)
      subscriptions -= this
    }
    override def add(): Unit = {
      commandSubscriptions.add(this)
      subscriptions += this → false
    }
  }

  protected class EventHyperbusSubscription(val requestMatcher: RequestMatcher,
                                            val group: String,
                                            val inputDeserializer: RequestDeserializer[RequestBase])
    extends HyperbusSubscription[RequestBase] {
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

