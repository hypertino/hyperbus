/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.util

import com.hypertino.hyperbus.transport.api.ServiceResolver
import scaldi.{Injectable, Injector}

class ServiceResolverInjector(implicit val inj: Injector) extends Injectable{
  def serviceResolver(name: Option[String]): ServiceResolver = name match {
    case None ⇒ inject[ServiceResolver]
    case Some(s) ⇒ inject[ServiceResolver] (identified by s)
  }
}

object ServiceResolverInjector {
  def apply(name: Option[String])(implicit inj: Injector) : ServiceResolver = {
    new ServiceResolverInjector()
      .serviceResolver(name)
  }
}
