package com.hypertino.hyperbus.util

import com.hypertino.hyperbus.transport.api.ServiceResolver
import scaldi.{Injectable, Injector}

class ServiceResolverInjector(implicit val inj: Injector) extends Injectable{
  def serviceResolver(name: Option[String]): ServiceResolver = name match {
    case None ⇒ inject[ServiceResolver]
    case Some(s) ⇒ inject[ServiceResolver] (identified by s and by default inject[ServiceResolver])
  }
}

object ServiceResolverInjector {
  def apply(name: Option[String])(implicit inj: Injector) : ServiceResolver = {
    new ServiceResolverInjector()
      .serviceResolver(name)
  }
}
