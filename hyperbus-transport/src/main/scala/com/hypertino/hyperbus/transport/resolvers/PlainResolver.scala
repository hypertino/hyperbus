package com.hypertino.hyperbus.transport.resolvers

import com.hypertino.hyperbus.model.RequestBase
import com.hypertino.hyperbus.transport.api.{NoTransportRouteException, ServiceEndpoint, ServiceResolver}
import monix.reactive.Observable

case class PlainEndpoint(hostname: String, port: Option[Int]) extends ServiceEndpoint

class PlainResolver(endpointsMap: Map[String, Seq[PlainEndpoint]]) extends ServiceResolver {
  def this(serviceName: String, hostname: String, port: Option[Int]) = this(Map(serviceName → Seq(PlainEndpoint(hostname, port))))

  override def serviceObservable(message: RequestBase): Observable[Seq[ServiceEndpoint]] = {
    endpointsMap.get(message.headers.hrl.authority) match {
      case Some(endpoints) ⇒ Observable.now(endpoints)
      case None ⇒ Observable.raiseError(new NoTransportRouteException(s"Can't resolve service for ${message.headers.hrl}"))
    }
  }
}
