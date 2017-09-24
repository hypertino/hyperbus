package com.hypertino.hyperbus.util

import com.hypertino.hyperbus.transport.api.ServiceRegistrator
import scaldi.{Injectable, Injector}

class ServiceRegistratorInjector(implicit val inj: Injector) extends Injectable{
  def registrator(name: Option[String]): ServiceRegistrator = name match {
    case None ⇒ inject[ServiceRegistrator]
    case Some(s) ⇒ inject[ServiceRegistrator] (identified by s and by default inject[ServiceRegistrator])
  }
}

object ServiceRegistratorInjector {
  def apply(name: Option[String])(implicit inj: Injector) : ServiceRegistrator = {
    new ServiceRegistratorInjector()
      .registrator(name)
  }
}
