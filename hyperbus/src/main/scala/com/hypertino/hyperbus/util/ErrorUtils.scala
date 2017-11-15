/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.util

import com.hypertino.hyperbus.model.{ErrorBody, InternalServerError, MessagingContext, ResponseBase}
import monix.eval.Task

import scala.util.Try

object ErrorUtils {
  def unexpected[T](r: Any)(implicit mcx: MessagingContext): T = throw InternalServerError(ErrorBody("unexpected", Some(s"Unexpected result: $r")))

  def unexpectedTask[T](r: Any)(implicit mcx: MessagingContext): Task[T] = Task.raiseError(InternalServerError(ErrorBody("unexpected", Some(s"Unexpected result: $r"))))

  def unexpected[T](implicit mcx: MessagingContext): PartialFunction[Try[_], T] = {
    case x ⇒
      unexpected(x)(mcx)
  }

  def unexpectedTask[T](implicit mcx: MessagingContext): PartialFunction[Try[_], Task[T]] = {
    case x ⇒ unexpectedTask(x)(mcx)
  }
}
