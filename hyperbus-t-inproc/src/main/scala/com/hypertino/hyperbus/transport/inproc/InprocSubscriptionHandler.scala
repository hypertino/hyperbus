package com.hypertino.hyperbus.transport.inproc

import java.io.StringReader

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import org.slf4j.LoggerFactory
import rx.lang.scala.Observer

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

private[transport] case class InprocSubscriptionHandler[REQ <: Request[Body]]
  (
    inputDeserializer: RequestDeserializer[REQ],
    handler: Either[REQ => Future[ResponseBase], Observer[REQ]]
  ) {

  def handleCommandOrEvent(serialize: Boolean,
                           subKey: SubKey,
                           message: RequestBase,
                           responseDeserializer: ResponseBaseDeserializer,
                           isPublish: Boolean,
                           resultPromise: Promise[ResponseBase])
                          (implicit executionContext: ExecutionContext): Boolean = {
    var commandHandlerFound = false
    var eventHandlerFound = false

    if (subKey.groupName.isEmpty) {
      // default subscription (groupName="") returns reply

      tryX("onCommand` deserialization failed",
        reserializeRequest(serialize, message, inputDeserializer)
      ) foreach { messageForSubscriber : REQ ⇒

        val matched = !serialize || subKey.requestMatcher.matchMessage(messageForSubscriber)

        if (matched) {
          commandHandlerFound = true
          requestHandler(messageForSubscriber) map { response ⇒
            if (!isPublish) {
              val finalResponse = if (serialize) {
                reserializeResponse(serialize, response, responseDeserializer)
              } else {
                response
              }
              resultPromise.success(finalResponse)
            }
          } recover {
            case NonFatal(e) ⇒
              InprocSubscriptionHandler.log.error("`onCommand` handler failed with", e)
          }
        }
        else {
          InprocSubscriptionHandler.log.error(s"Message ($messageForSubscriber) is not matched after serialize and deserialize")
        }
      }
    } else {
      tryX("onEvent` deserialization failed",
        reserializeRequest(serialize, message, inputDeserializer)
      ) foreach { messageForSubscriber: REQ ⇒

        val matched = !serialize || subKey.requestMatcher.matchMessage(messageForSubscriber)

        if (matched) {
          eventHandlerFound = true
          eventHandler.onNext(messageForSubscriber)
        }
        else {
          InprocSubscriptionHandler.log.error(s"Message ($messageForSubscriber) is not matched after serialize and deserialize")
        }
      }
    }
    commandHandlerFound || eventHandlerFound
  }

  private def reserializeRequest(serialize: Boolean, message: RequestBase, deserializer: RequestDeserializer[REQ]): REQ = {
    if (serialize) {
      MessageDeserializer.deserializeRequestWith(
        message.serializeToString
      )(deserializer)
    }
    else {
      message.asInstanceOf[REQ]
    }
  }

  private def reserializeResponse[OUT <: ResponseBase](serialize: Boolean, message: ResponseBase, deserializer: ResponseDeserializer[OUT]): OUT = {
    if (serialize) {
      MessageDeserializer.deserializeResponseWith(message.toString)(deserializer)
    }
    else {
      message.asInstanceOf[OUT]
    }
  }

  private def tryX[T](failMsg: String, code: ⇒ T): Option[T] = {
    try {
      Some(code)
    }
    catch {
      case NonFatal(e) ⇒
        InprocSubscriptionHandler.log.error(failMsg, e)
        None
    }
  }

  private def requestHandler = handler.left.get

  private def eventHandler = handler.right.get
}

object InprocSubscriptionHandler {
  protected val log = LoggerFactory.getLogger(this.getClass)
}