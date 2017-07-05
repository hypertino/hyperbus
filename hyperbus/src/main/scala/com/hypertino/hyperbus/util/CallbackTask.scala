package com.hypertino.hyperbus.util

import monix.eval.Task

class CallbackTask[B] extends PromiseCallback[B] {
  val task: Task[B] = Task.fromFuture(future)
}

object CallbackTask {
  def apply[B] = new CallbackTask[B]()
}