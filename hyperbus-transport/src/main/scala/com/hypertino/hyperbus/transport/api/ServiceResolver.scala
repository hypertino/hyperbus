package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.model.HRL
import monix.eval.Task
import monix.reactive.Observable

trait ServiceResolver {
  def lookupService(hrl: HRL): Task[ServiceEndpoint] = {
    serviceObservable(hrl).firstL.map(_.head)
  }
  def serviceObservable(hrl: HRL): Observable[Seq[ServiceEndpoint]]
}
