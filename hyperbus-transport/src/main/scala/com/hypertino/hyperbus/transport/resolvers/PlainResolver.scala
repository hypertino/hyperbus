package com.hypertino.hyperbus.transport.resolvers

import java.net.{URI}

import com.hypertino.hyperbus.model.HRL
import com.hypertino.hyperbus.transport.api.{NoTransportRouteException, ServiceEndpoint, ServiceResolver}
import monix.reactive.Observable

case class PlainEndpoint(hostname: String, port: Option[Int]) extends ServiceEndpoint

class PlainResolver(endpointsMap: Map[String, Seq[PlainEndpoint]]) extends ServiceResolver {
  def this(serviceName: String, hostname: String, port: Option[Int]) = this(Map(serviceName → Seq(PlainEndpoint(hostname, port))))

  override def serviceObservable(hrl: HRL): Observable[Seq[ServiceEndpoint]] = {
    val uri = new URI(hrl.location)
    val service = uri.getScheme + "://" + uri.getAuthority
    endpointsMap.get(service) match {
      case Some(endpoints) ⇒ Observable.now(endpoints)
      case None ⇒ Observable.raiseError(new NoTransportRouteException(s"Can't resolve service for $hrl"))
    }
  }
}
