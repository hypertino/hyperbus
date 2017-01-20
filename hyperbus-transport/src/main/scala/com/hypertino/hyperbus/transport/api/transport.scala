package com.hypertino.hyperbus.transport.api

import java.io.{StringWriter, Writer}

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization.{RequestDeserializer, ResponseBaseDeserializer, ResponseDeserializer}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.transport.api.uri.Uri
import rx.lang.scala.Observer

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait PublishResult {
  def sent: Option[Boolean]

  def offset: Option[String]
}

trait ClientTransport {
  def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Future[ResponseBase]

  def publish(message: RequestBase): Future[PublishResult]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

trait Subscription

case class EventStreamSubscription(observableSubscription: rx.lang.scala.Subscription, transportSubscription: Subscription) extends Subscription

trait ServerTransport {
  // todo: instead of ((Request[Body]) => Future[ResponseBase]) use class like Observer[-T] with contravariance
  def onCommand[REQ <: Request[Body]](matcher: RequestMatcher,
                inputDeserializer: RequestDeserializer[REQ])
               (handler: (REQ) => Future[ResponseBase]): Future[Subscription]

  def onEvent[REQ <: Request[Body]](matcher: RequestMatcher,
              groupName: String,
              inputDeserializer: RequestDeserializer[REQ],
              observer: Observer[REQ]): Future[Subscription]

  def off(subscription: Subscription): Future[Unit]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

class NoTransportRouteException(message: String) extends RuntimeException(message)

