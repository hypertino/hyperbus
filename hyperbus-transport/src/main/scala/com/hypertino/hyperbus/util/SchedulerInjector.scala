package com.hypertino.hyperbus.util

import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

class SchedulerInjector(implicit val inj: Injector) extends Injectable{
  def scheduler(name: Option[String]): Scheduler = name match {
    case None ⇒ inject[Scheduler]
    case Some(s) ⇒ inject[Scheduler] (identified by s and by default inject[Scheduler])
  }
}

object SchedulerInjector {
  def apply(name: Option[String])(implicit inj: Injector) : Scheduler = {
    new SchedulerInjector()
    .scheduler(name)
  }
}
