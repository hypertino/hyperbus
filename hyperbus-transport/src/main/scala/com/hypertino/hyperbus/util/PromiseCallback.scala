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