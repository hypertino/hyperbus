package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.eval.Task
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
  * Manages transport layer based on provided route configuration.
  *
  * @param clientRoutes     - routes clients/consumer calls to specific transport
  * @param serverRoutes     - routes messages from specific transport to server/producer subscribed on topic
  * @param executionContext - execution context used by transport layer
  */
class TransportManager(protected[this] val clientRoutes: Seq[TransportRoute[ClientTransport]],
                       protected[this] val serverRoutes: Seq[TransportRoute[ServerTransport]],
                       implicit protected[this] val executionContext: ExecutionContext) extends ClientTransport with ServerTransport {

  protected[this] val log = LoggerFactory.getLogger(this.getClass)

  def this(configuration: TransportConfiguration) = this(configuration.clientRoutes,
    configuration.serverRoutes, ExecutionContext.global)

  def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Task[ResponseBase] = {
    this.lookupClientTransport(message).ask(message, responseDeserializer)
  }

  def publish(message: RequestBase): Task[PublishResult] = {
    this.lookupClientTransport(message).publish(message)
  }

  def commands[REQ <: Request[Body]](matcher: RequestMatcher,
                                     inputDeserializer: RequestDeserializer[REQ]): Observable[CommandEvent[REQ]] = {
    lookupServerTransport(matcher).commands(matcher, inputDeserializer)
  }

  def events[REQ <: Request[Body]](matcher: RequestMatcher,
                                   groupName: String,
                                   inputDeserializer: RequestDeserializer[REQ]): Observable[REQ] = {

    lookupServerTransport(matcher).events(matcher, groupName, inputDeserializer)
  }

  protected def lookupClientTransport(message: RequestBase): ClientTransport = {
    clientRoutes.find { route ⇒
      route.matcher.matchMessage(message)
    } map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"Message headers: ${message.headers}")
    }
  }

  protected def lookupServerTransport(requestMatcher: RequestMatcher): ServerTransport = {
    serverRoutes.find { route ⇒
      route.matcher.matchRequestMatcher(requestMatcher)
    } map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"${requestMatcher.headers}")
    }
  }

  def shutdown(duration: FiniteDuration): Task[Boolean] = {
    Task.zipMap2(
      Task.gatherUnordered(clientRoutes.map(_.transport.shutdown(duration))),
      Task.gatherUnordered(serverRoutes.map(_.transport.shutdown(duration)))
    ) { (c, s) ⇒
      s.forall(_ == true) && c.forall(_ == true)
    }
  }
}
