/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport

import com.hypertino.hyperbus.config.HyperbusConfigurationLoader
import com.hypertino.hyperbus.model.{Body, Request, RequestBase, ResponseBase}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers._
import com.hypertino.hyperbus.transport.registrators.DummyRegistrator
import com.typesafe.config.{Config, ConfigFactory}
import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import monix.reactive.Observable
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}
import scaldi.{Injector, Module}

import scala.concurrent.duration.FiniteDuration

class MockClientTransport(config: Config, inj: Injector) extends ClientTransport {
  override def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Task[ResponseBase] = ???

  override def shutdown(duration: FiniteDuration): Task[Boolean] = ???

  override def publish(message: RequestBase): Task[PublishResult] = ???
}

class MockServerTransport(config: Config, inj: Injector) extends ServerTransport {
  def commands[REQ <: RequestBase](matcher: RequestMatcher,
                                     inputDeserializer: RequestDeserializer[REQ]): Observable[CommandEvent[REQ]] = ???

  def events[REQ <: RequestBase](matcher: RequestMatcher,
                                   groupName: String,
                                   inputDeserializer: RequestDeserializer[REQ]): Observable[REQ] = ???

  override def shutdown(duration: FiniteDuration): Task[Boolean] = ???

  override def startServices(): Unit = {}
}

// todo: document match syntax

class HyperbusConfigurationTest extends FreeSpec with ScalaFutures with Matchers {
  "Configuration Test" in {
    val config = ConfigFactory.parseString(
      """
        hyperbus: {
          transports: {
            mock-client.class-name: com.hypertino.hyperbus.transport.MockClientTransport,
            mock-server.class-name: com.hypertino.hyperbus.transport.MockServerTransport,
          },
          client-routes: [
            {
              match: {
                r.l: "/topic"
                r.q.id: ["100500", "100501"]
                r.q.name: "\\*"
                m: "post"
              }
              transport: mock-client
            }
          ],
          server-routes: [
            {
              match: {
                r.l: "~/topic/.*"
                m: "*"
              }
              transport: mock-server
            }
          ]
        }
        """)

    implicit val injector = new Module {
      bind[Scheduler] to monix.execution.Scheduler.Implicits.global
      bind[ServiceRegistrator] to DummyRegistrator
    }
    val conf = HyperbusConfigurationLoader.fromConfig(config, injector)

    conf.defaultGroupName shouldBe empty
    conf.readMessagesLogLevel shouldBe "TRACE"

    conf.clientRoutes should not be empty
    conf.clientRoutes.head.matcher.headers should contain theSameElementsAs Map(
      "r.l" → Seq(Specific("/topic")),
      "r.q.id" → Seq(Specific("100500"), Specific("100501")),
      "r.q.name" → Seq(Specific("*")),
      "m" → Seq(Specific("post"))
    )
    conf.clientRoutes.head.transport shouldBe a[MockClientTransport]

    conf.serverRoutes should not be empty

    conf.serverRoutes.head.matcher.headers should contain theSameElementsAs Map(
      "r.l" → Seq(RegexMatcher("/topic/.*")),
      "m" → Seq(Any)
    )
    conf.serverRoutes.head.transport shouldBe a[MockServerTransport]
  }

}
