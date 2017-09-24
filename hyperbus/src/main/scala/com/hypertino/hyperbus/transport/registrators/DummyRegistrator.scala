package com.hypertino.hyperbus.transport.registrators

import com.hypertino.hyperbus.transport.api.ServiceRegistrator
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.eval.Task
import monix.execution.Cancelable

object DummyRegistrator extends ServiceRegistrator{
  override def registerService(requestMatcher: RequestMatcher): Task[Cancelable] = Task.now(Cancelable.empty)
}
