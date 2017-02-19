package com.hypertino.hyperbus.transport

import com.typesafe.config.{Config, ConfigFactory}
import com.hypertino.hyperbus.model.{Body, Request, RequestBase, ResponseBase}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}
import rx.lang.scala.Observer

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MockClientTransport(config: Config) extends ClientTransport {
  override def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Future[ResponseBase] = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???

  override def publish(message: RequestBase): Future[PublishResult] = ???
}

class MockServerTransport(config: Config) extends ServerTransport {
  override def onCommand[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                         inputDeserializer: RequestDeserializer[REQ])
                        (handler: (REQ) => Future[ResponseBase]): Future[Subscription] = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???

  override def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                       groupName: String,
                       inputDeserializer: RequestDeserializer[REQ],
                       subscriber: Observer[REQ]): Future[Subscription] = ???

  override def off(subscription: Subscription): Future[Unit] = ???
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
