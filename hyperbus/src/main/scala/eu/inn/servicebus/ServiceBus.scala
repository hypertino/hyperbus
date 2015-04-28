package eu.inn.servicebus

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.impl.{Subscriptions, ServiceBusMacro}
import eu.inn.servicebus.serialization.{Decoder, Encoder}
import eu.inn.servicebus.transport.{SubscriptionHandlerResult, ClientTransport, ServerTransport}

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.language.experimental.macros

trait ServiceBusBase {
  def ask[OUT,IN](
                    topic: String,
                    message: IN,
                    inputEncoder: Encoder[IN],
                    outputDecoder: Decoder[OUT]
                    ): Future[OUT]

  def on[OUT,IN](topic: String, groupName: Option[String], inputDecoder: Decoder[IN])
                       (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def off(subscriptionId: String): Unit
}

class ServiceBus(val defaultClientTransport: ClientTransport, val defaultServerTransport: ServerTransport)
  extends ServiceBusBase {

  protected val clientRoutes = new TrieMap[String, ClientTransport]
  protected val serverRoutes = new TrieMap[String, ServerTransport]
  protected val subscriptions = new Subscriptions[String]

  def ask[OUT,IN](
                    topic: String,
                    message: IN
                    ): Future[OUT] = macro ServiceBusMacro.ask[OUT,IN]

  def ask[OUT,IN](
                    topic: String,
                    message: IN,
                    inputEncoder: Encoder[IN],
                    outputDecoder: Decoder[OUT]
                    ): Future[OUT] = {
    this.lookupClientTransport(topic).ask[OUT,IN](topic,message,inputEncoder,outputDecoder)
  }

  def on[OUT,IN](topic: String, groupName: Option[String])
                       (handler: (IN) => Future[OUT]): String = macro ServiceBusMacro.on[OUT,IN]

  def off(subscriptionId: String): Unit = {
    subscriptions.getRouteKeyById(subscriptionId) foreach { topic =>
      subscriptions.get(topic) foreach { case (_,subscrSeq) =>
        subscrSeq.find(_.subscriptionId == subscriptionId).foreach {
          underlyingSubscription =>
            lookupServerTransport(topic).off(underlyingSubscription.subscription)
        }
      }
    }
    subscriptions.remove(subscriptionId)
  }
  def on[OUT,IN](topic: String, groupName: Option[String], inputDecoder: Decoder[IN])
                       (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {

    val underlyingSubscriptionId = lookupServerTransport(topic: String).on[OUT,IN](topic, groupName, inputDecoder)(handler)

    subscriptions.add(topic,None,underlyingSubscriptionId)
  }

  protected def lookupServerTransport(topic: String): ServerTransport =
    serverRoutes.getOrElse(topic, defaultServerTransport)

  protected def lookupClientTransport(topic: String): ClientTransport =
    clientRoutes.getOrElse(topic, defaultClientTransport)
}
