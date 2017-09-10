package com.hypertino.hyperbus.subscribe

import com.hypertino.hyperbus.model.{ErrorBody, ErrorResponseBase, HyperbusError, InternalServerError, MessagingContext}
import com.typesafe.scalalogging.Logger

import scala.util.control.NonFatal

object SubscribeMacroUtil {
  def convertUnhandledException(log: Option[Logger] = None)
                               (implicit mcx: MessagingContext): PartialFunction[Throwable, ErrorResponseBase] = {
    case h: ErrorResponseBase ⇒ h
    case NonFatal(other) ⇒
      val result = InternalServerError(
        ErrorBody("unhandled-exception", Some(other.getClass.toString + {
          if (other.getMessage != null) {": " + other.getMessage} else {""}
        }))
      )
      log.foreach(_.error(result.body.toString, other))
      result
  }
}
