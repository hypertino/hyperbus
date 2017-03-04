package com.hypertino.hyperbus

import java.io.{Reader, Writer}

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.impl.MacroApi
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.util.LogUtils
import org.slf4j.LoggerFactory
import rx.lang.scala.{Observable, Observer, Subscriber}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.experimental.macros
import scala.util.Try

class Hyperbus(val transportManager: TransportManager,
               val defaultGroupName: Option[String] = None,
               val logMessages: Boolean = true)(implicit val executionContext: ExecutionContext)
  extends HyperbusApi {

  import LogUtils._

  protected val log = LoggerFactory.getLogger(this.getClass)

  def onEvent(requestMatcher: RequestMatcher, groupName: Option[String], observer: Observer[DynamicRequest]): Future[Subscription] = {
    onEvent[DynamicRequest](requestMatcher, groupName, DynamicRequest.apply, observer)
  }

  def onCommand(requestMatcher: RequestMatcher)
               (handler: DynamicRequest => Future[_ <: Response[Body]]): Future[Subscription] = {
    onCommand[Response[Body], DynamicRequest](requestMatcher, DynamicRequest.apply)(handler)
  }


  protected class RequestReplySubscription[REQ <: Request[Body]](val handler: (REQ) => Future[Response[Body]],
                                                                 val requestDeserializer: RequestDeserializer[REQ]) {
    def underlyingHandler(in: REQ): Future[ResponseBase] = {
      if (logMessages && log.isTraceEnabled) {
        log.trace(Map("messageId" → in.headers.messageId, "correlationId" → in.correlationId,
          "subscriptionId" → this.hashCode.toHexString), s"hyperbus ~> $in")
      }
      implicit val mcx: MessagingContext = in
      val futureOut = handler(in) recover {
        case z: Response[_] ⇒ z
        case t: Throwable ⇒ unhandledException(in, t)
      }
      if (logMessages && log.isTraceEnabled) {
        futureOut map { out ⇒
          log.trace(Map("messageId" → out.headers.messageId, "correlationId" → out.headers.correlationId,
            "subscriptionId" → this.hashCode.toHexString), s"hyperbus <~(R)~  $out")
          out
        }
      } else {
        futureOut
      }
    }
  }

  def ask[RESP <: Response[Body], REQ <: Request[Body]](request: REQ,
                                                        responseDeserializer: ResponseDeserializer[RESP]): Future[RESP] = {

    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → request.headers.messageId, "correlationId" → request.correlationId), s"hyperbus <~ $request")
    }
    transportManager.ask(request, responseDeserializer) map { r ⇒
      (r: @unchecked) match {
        case throwable: Throwable ⇒
          throw throwable
        case other: RESP @unchecked ⇒
          if (logMessages && log.isTraceEnabled) {
            log.trace(Map("messageId" → other.headers.messageId, "correlationId" → other.headers.correlationId), s"hyperbus ~(R)~> $other")
          }
          other
      }
    }
  }

  def publish[REQ <: Request[Body]](request: REQ): Future[PublishResult] = {
    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → request.headers.messageId, "correlationId" → request.correlationId), s"hyperbus <| $request")
    }
    transportManager.publish(request)
  }

  def onCommand[RESP <: Response[Body], REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                                              requestDeserializer: RequestDeserializer[REQ])
                                                             (handler: (REQ) => Future[RESP]): Future[Subscription] = {

    val subscription = new RequestReplySubscription[REQ](handler, requestDeserializer)
    transportManager.onCommand(requestMatcher, subscription.requestDeserializer)(subscription.underlyingHandler)
  }

  def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                    groupName: Option[String],
                                    requestDeserializer: RequestDeserializer[REQ],
                                    observer: Observer[REQ]): Future[Subscription] = {
    val transportSubscriptionPromise = Promise[Subscription]()
    val observableSubscription = Observable { subscriber: Subscriber[REQ] ⇒
      val finalGroupName = groupName.getOrElse {
        defaultGroupName.getOrElse {
          throw new UnsupportedOperationException(s"Can't subscribe: group name is not defined")
        }
      }
      transportSubscriptionPromise.completeWith(
        transportManager.onEvent(requestMatcher, finalGroupName, requestDeserializer, subscriber)
      )
    }.subscribe(observer)
    transportSubscriptionPromise.future map { transportSubscription ⇒
      EventStreamSubscription(observableSubscription, transportSubscription)
    }
  }

  def off(subscription: Subscription): Future[Unit] = {
    subscription match {
      case EventStreamSubscription(observableSubscription, transportSubscription) ⇒
        transportManager.off(transportSubscription) map { _ ⇒
          observableSubscription.unsubscribe()
        }
      case _ ⇒
        transportManager.off(subscription)
    }
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    transportManager.shutdown(duration)
  }

//  protected def exceptionSerializer(exception: Throwable, writer: Writer): Unit = {
//    exception match {
//      case r: ErrorResponse ⇒ r.serialize(writer)
//      case t ⇒
//        val error = InternalServerError(ErrorBody(DefError.INTERNAL_ERROR, Some(t.getMessage)))
//        logError("Unhandled exception", error, t)
//        error.serialize(writer)
//    }
//  }

  val macroApiImpl = new MacroApi {
    def responseDeserializer(reader: Reader,
                             headersObj: Obj,
                             bodyDeserializer: PartialFunction[ResponseHeaders, ResponseBodyDeserializer]): Response[Body] = {
      StandardResponse(reader, headersObj, bodyDeserializer)
    }
  }

  protected def logError(msg: String, error: HyperbusException[ErrorBody], throwable: Throwable): Unit = {
    log.error(msg + ". #" + error.body.errorId, throwable)
  }

  protected def safeErrorMessage(msg: String, request: Request[Body]): String = {
    msg + " " + safe(() => request.headers.hri.toString) + safe(() => request.headers.toString)
  }

  protected def unhandledException(implicit request: Request[Body], exception: Throwable): Response[Body] = {
    val errorBody = ErrorBody(DefError.INTERNAL_ERROR, Some(
      safeErrorMessage(s"Unhandled exception: ${exception.getMessage}", request)
    ))
    log.error(errorBody.message + ". #" + errorBody.errorId, exception)
    InternalServerError(errorBody)
  }

  //protected def responseSerializerNotFound(response: Response[Body]) = log.error("Can't serialize response: {}", response)

  protected def safe(t: () => String): String = Try(t()).getOrElse("???")
}
