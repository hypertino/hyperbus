/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.util

import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

class SchedulerInjector(implicit val inj: Injector) extends Injectable{
  def scheduler(name: Option[String]): Scheduler = name match {
    case None ⇒ inject[Scheduler]
    case Some(s) ⇒ inject[Scheduler] (identified by s)
  }
}

object SchedulerInjector {
  def apply(name: Option[String])(implicit inj: Injector) : Scheduler = {
    new SchedulerInjector()
    .scheduler(name)
  }
}
