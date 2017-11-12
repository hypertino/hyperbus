/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.subscribe

import com.hypertino.hyperbus.model.{ErrorBody, ErrorResponseBase, HyperbusError, InternalServerError, MessagingContext}
import com.typesafe.scalalogging.Logger

import scala.util.control.NonFatal

object SubscribeMacroUtil {
  def convertUnhandledException(log: Option[Logger] = None)
                               (implicit mcx: MessagingContext): PartialFunction[Throwable, ErrorResponseBase] = {
    case h: ErrorResponseBase @unchecked ⇒ h
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
