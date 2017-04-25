package com.hypertino.hyperbus.transport.api

import monix.eval.Task
import monix.reactive.Observable

trait ServiceResolver {
  def lookupService(serviceName: String): Task[ServiceEndpoint] = {
    serviceObservable(serviceName).firstL.map(_.head)
  }
  def serviceObservable(serviceName: String): Observable[Seq[ServiceEndpoint]]
}
