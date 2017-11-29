/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.util

import com.hypertino.hyperbus.transport.api.ServiceRegistrator
import scaldi.{Injectable, Injector}

class ServiceRegistratorInjector(implicit val inj: Injector) extends Injectable{
  def registrator(name: Option[String]): ServiceRegistrator = name match {
    case None ⇒ inject[ServiceRegistrator]
    case Some(s) ⇒ inject[ServiceRegistrator] (identified by s)
  }
}

object ServiceRegistratorInjector {
  def apply(name: Option[String])(implicit inj: Injector) : ServiceRegistrator = {
    new ServiceRegistratorInjector()
      .registrator(name)
  }
}
