/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.util

import monix.eval.Callback

import scala.concurrent.{Future, Promise}

class PromiseCallback[B] extends Callback[B] {
  val promise: Promise[B] = Promise[B]
  def future: Future[B] = promise.future
  override def onSuccess(value: B): Unit = promise.success(value)
  override def onError(ex: Throwable): Unit = promise.failure(ex)
}

object PromiseCallback {
  def apply[B] = new PromiseCallback[B]
}