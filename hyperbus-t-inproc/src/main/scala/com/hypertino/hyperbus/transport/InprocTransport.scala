package com.hypertino.hyperbus.transport

import java.io.StringReader

import com.typesafe.config.Config
import com.hypertino.hyperbus.model.{Body, Message, Request, RequestBase, ResponseBase}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.transport.inproc.{InprocCommandSubscription, InprocEventSubscription}
import com.hypertino.hyperbus.util.ConfigUtils._
import com.hypertino.hyperbus.util.FuzzyIndex
import org.slf4j.{Logger, LoggerFactory}
import rx.lang.scala.Observer

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Random

// todo: log messages?
class InprocTransport(serialize: Boolean = false)
                     (implicit val executionContext: ExecutionContext) extends ClientTransport with ServerTransport {

  def this(config: Config) = this(
    serialize = config.getOptionBoolean("serialize").getOrElse(false)
  )(
    scala.concurrent.ExecutionContext.global // todo: configurable ExecutionContext like in akka?
  )

  protected val commandSubscriptions = new FuzzyIndex[InprocCommandSubscription]
  protected val eventSubscriptions = new FuzzyIndex[InprocEventSubscription]
  protected val log: Logger = LoggerFactory.getLogger(this.getClass)

  // todo: refactor this method, it's awful
  protected def _ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer, isPublish: Boolean): Future[_] = {
    // todo: handle serialization exceptions

    val resultPromise = Promise[Any]
    val serialized = serialize(message)
    val handled = getRandom(commandSubscriptions
      .lookupAll(message)).map { subscription ⇒

      val request: RequestBase = serialized.map { reader ⇒
        MessageReader.read(reader, subscription.inputDeserializer)
      } getOrElse {
        message
      }

      val resultFuture = subscription.handler(request)
      if (serialize) {
        resultPromise.completeWith {
          resultFuture.map { result ⇒
            MessageReader.read(new StringReader(result.serializeToString), responseDeserializer)
          } recover {
            case r: ResponseBase ⇒
              MessageReader.read(new StringReader(r.serializeToString), responseDeserializer)
          }
        }
      }
      else {
        resultPromise.completeWith(resultFuture)
      }
      true
    } getOrElse {
      false
    }

    if (!isPublish && !handled) {
      Future.failed {
        new NoTransportRouteException(s"${message.headers.hri.serviceAddress} is not found. Headers: ${message.headers}")
      }
    }
    else {
      eventSubscriptions
        .lookupAll(message)
        .groupBy(_.group)
        .foreach { subscriptions ⇒
          val subscription = getRandom(subscriptions._2).get

          val request: RequestBase = serialized.map { reader ⇒
            MessageReader.read(reader, subscription.inputDeserializer)
          } getOrElse {
            message
          }

          subscription.handler.onNext(request)
        }

      if (isPublish) {
        Future.successful {
          new PublishResult {
            def sent = Some(true)
            def offset = None
            override def toString = s"PublishResult(sent=Some(true),offset=None)"
          }
        }
      } else {
        resultPromise.future
      }
    }
  }

  override def ask(message: RequestBase, outputDeserializer: ResponseBaseDeserializer): Future[ResponseBase] = {
    _ask(message, outputDeserializer, isPublish = false).asInstanceOf[Future[ResponseBase]]
  }

  override def publish(message: RequestBase): Future[PublishResult] = {
    _ask(message, null, isPublish = true).asInstanceOf[Future[PublishResult]]
  }

  override def onCommand[REQ <: Request[Body]](matcher: RequestMatcher,
                         inputDeserializer: RequestDeserializer[REQ])
                        (handler: (REQ) => Future[ResponseBase]): Future[Subscription] = {

    val s = InprocCommandSubscription(matcher, inputDeserializer, handler.asInstanceOf[RequestBase ⇒ Future[ResponseBase]])
    commandSubscriptions.add(s)
    Future.successful(s)
  }

  override def onEvent[REQ <: Request[Body]](matcher: RequestMatcher,
                       groupName: String,
                       inputDeserializer: RequestDeserializer[REQ],
                       observer: Observer[REQ]): Future[Subscription] = {

    val s = InprocEventSubscription(matcher, groupName, inputDeserializer, observer.asInstanceOf[Observer[RequestBase]])
    eventSubscriptions.add(s)
    Future.successful(s)
  }

  override def off(subscription: Subscription): Future[Unit] = {
    subscription match {
      case i: InprocCommandSubscription ⇒
        Future {
          commandSubscriptions.remove(i)
        }

      case i: InprocEventSubscription ⇒
        Future {
          eventSubscriptions.remove(i)
        }

      case other ⇒
        Future.failed {
          new ClassCastException(s"InprocSubscription expected instead of ${other.getClass}")
        }
    }
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    eventSubscriptions.clear()
    commandSubscriptions.clear()
    Future.successful(true)
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
}
