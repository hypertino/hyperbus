package com.hypertino.hyperbus.transport.api

import monix.eval.Task

trait ServiceResolver {
  def lookupService(serviceName: String): Task[ServiceEndpoint]
}
