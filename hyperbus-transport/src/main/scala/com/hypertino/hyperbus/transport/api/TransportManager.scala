package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import org.slf4j.LoggerFactory
import rx.lang.scala.Subscriber

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
                       implicit protected[this] val executionContext: ExecutionContext) extends TransportManagerApi {

  protected[this] val log = LoggerFactory.getLogger(this.getClass)

  def this(configuration: TransportConfiguration) = this(configuration.clientRoutes,
    configuration.serverRoutes, ExecutionContext.global)

  def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Future[ResponseBase] = {
    this.lookupClientTransport(message).ask(message, responseDeserializer)
  }

  def publish(message: RequestBase): Future[PublishResult] = {
    this.lookupClientTransport(message).publish(message)
  }

  protected def lookupClientTransport(message: RequestBase): ClientTransport = {
    clientRoutes.find { route ⇒
      route.matcher.matchMessage(message)
    } map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"${message.uri} with headers: ${message.headers}")
    }
  }

  def off(subscription: Subscription): Future[Unit] = {
    subscription match {
      case TransportSubscription(transport, underlyingSubscription) ⇒
        transport.off(underlyingSubscription)
      case other ⇒
        Future.failed {
          new ClassCastException(s"TransportSubscription expected but ${other.getClass} is received")
        }
    }
  }

  def onCommand[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                inputDeserializer: RequestDeserializer[REQ])
               (handler: (REQ) => Future[ResponseBase]): Future[Subscription] = {

    val transport = lookupServerTransport(requestMatcher)
    transport.onCommand(
      requestMatcher,
      inputDeserializer)(handler) map { underlyingSubscription ⇒

      val subscription = TransportSubscription(transport, underlyingSubscription)
      log.info(s"New `onCommand` subscription on $requestMatcher: #${handler.hashCode.toHexString}. $subscription")
      subscription
    }
  }

  def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
              groupName: String,
              inputDeserializer: RequestDeserializer[REQ],
              subscriber: Subscriber[REQ]): Future[Subscription] = {

    val transport = lookupServerTransport(requestMatcher)
    transport.onEvent(
      requestMatcher,
      groupName,
      inputDeserializer,
      subscriber) map { underlyingSubscription ⇒

      val subscription = TransportSubscription(transport, underlyingSubscription)
      log.info(s"New `onEvent` subscription on $requestMatcher: #$subscription")
      subscription
    }
  }

  protected def lookupServerTransport(requestMatcher: RequestMatcher): ServerTransport = {
    serverRoutes.find { route ⇒
      route.matcher.matchRequestMatcher(requestMatcher)
    } map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"${requestMatcher.uri} with header matchers: ${requestMatcher.headers}")
    }
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    val client = Future.sequence(clientRoutes.map(_.transport.shutdown(duration)))
    val server = Future.sequence(serverRoutes.map(_.transport.shutdown(duration)))
    client flatMap { c ⇒
      server map { s ⇒
        s.forall(_ == true) && c.forall(_ == true)
      }
    }
  }
}

private[transport] case class TransportSubscription(
                                                     transport: ServerTransport,
                                                     underlyingSubscription: Subscription
                                                   ) extends Subscription