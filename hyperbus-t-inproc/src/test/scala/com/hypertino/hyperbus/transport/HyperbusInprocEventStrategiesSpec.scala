/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport

import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.transport.api.{ClientTransportRoute, ServerTransportRoute}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.transport.registrators.DummyRegistrator
import monix.execution.Scheduler
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import scaldi.Module
import monix.execution.Scheduler.Implicits.global

class HyperbusInprocEventStrategiesSpec extends FlatSpec  with ScalaFutures with Matchers {
  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(10000, Millis)))

  "Unbound" should "back pressure if buffer is full" in {


  }


  def newHyperbus() = {
    implicit val injector = new Module {
      bind [Scheduler] to global
    }
    val tr = new InprocTransport
    val cr = List(ClientTransportRoute(tr, RequestMatcher.any))
    val sr = List(ServerTransportRoute(tr, RequestMatcher.any, DummyRegistrator))
    new Hyperbus(Some("group1"),
      readMessagesLogLevel = "TRACE", writeMessagesLogLevel = "DEBUG",
      serverReadMessagesLogLevel = "TRACE", serverWriteMessagesLogLevel = "DEBUG",
      cr, sr, global, injector)
  }
}
