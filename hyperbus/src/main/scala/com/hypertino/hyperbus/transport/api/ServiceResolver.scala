package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.model.{HRL, MessageBase, RequestBase}
import monix.eval.Task
import monix.reactive.Observable

trait ServiceResolver {
  def lookupService(message: RequestBase): Task[ServiceEndpoint] = {
    serviceObservable(message).firstL.map(_.headOption.getOrElse{
      throw new NoTransportRouteException(s"Can't resolve: ${message.headers.hrl}")
    })
  }
  def serviceObservable(message: RequestBase): Observable[Seq[ServiceEndpoint]]
}
