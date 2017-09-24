package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.eval.Task
import monix.execution.Cancelable

trait ServiceRegistrator {
  def registerService(requestMatcher: RequestMatcher): Task[Cancelable]
}
