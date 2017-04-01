package com.hypertino.hyperbus.transport.resolvers

import com.hypertino.hyperbus.transport.api.{ServiceEndpoint, ServiceResolver}
import monix.eval.Task

case class PlainEndpoint(hostname: String) extends ServiceEndpoint {
  override def port: Option[Int] = None
}

class PlainResolver extends ServiceResolver {
  override def lookupService(serviceName: String): Task[ServiceEndpoint] = {
    Task.now(PlainEndpoint(serviceName))
  }
}
