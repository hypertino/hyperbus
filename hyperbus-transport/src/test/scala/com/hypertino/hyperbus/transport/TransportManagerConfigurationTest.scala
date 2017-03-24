package com.hypertino.hyperbus.transport

import com.hypertino.hyperbus.model.{Body, Request, RequestBase, ResponseBase}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers._
import com.typesafe.config.{Config, ConfigFactory}
import monix.eval.Task
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.duration.FiniteDuration

class MockClientTransport(config: Config) extends ClientTransport {
  override def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Task[ResponseBase] = ???

  override def shutdown(duration: FiniteDuration): Task[Boolean] = ???

  override def publish(message: RequestBase): Task[PublishResult] = ???
}

class MockServerTransport(config: Config) extends ServerTransport {
  def commands[REQ <: RequestBase](matcher: RequestMatcher,
                                     inputDeserializer: RequestDeserializer[REQ]): Observable[CommandEvent[REQ]] = ???

  def events[REQ <: RequestBase](matcher: RequestMatcher,
                                   groupName: String,
                                   inputDeserializer: RequestDeserializer[REQ]): Observable[REQ] = ???

  override def shutdown(duration: FiniteDuration): Task[Boolean] = ???
}

class TransportManagerConfigurationTest extends FreeSpec with ScalaFutures with Matchers {
  "Transport Manager" - {
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
                u: {
                  value: "/topic", type: Specific
                }
                m: {
                  value: "post"
                }
              }
              transport: mock-client
            }
          ],
          server-routes: [
            {
              match: {
                u: {
                  value: "/topic/.*", type: Regex
                }
              }
              transport: mock-server
            }
          ]
        }
        """)

      val sbc = TransportConfigurationLoader.fromConfig(config)

      sbc.clientRoutes should not be empty
      sbc.clientRoutes.head.matcher.headers should contain theSameElementsAs Map("u" → Specific("/topic"), "m" → Specific("post"))
      sbc.clientRoutes.head.transport shouldBe a[MockClientTransport]

      sbc.serverRoutes should not be empty
      sbc.serverRoutes.head.matcher.headers should contain theSameElementsAs Map("u" → RegexMatcher("/topic/.*"))
      sbc.serverRoutes.head.transport shouldBe a[MockServerTransport]
    }
  }
}
