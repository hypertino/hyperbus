package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import rx.lang.scala.Subscriber

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * This is an API to manage generic transport layer.
  * Has no knowledge about underlying data model.
  */

trait TransportManagerApi {
  def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Future[ResponseBase]

  def publish(message: RequestBase): Future[PublishResult]

  def onCommand[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                inputDeserializer: RequestDeserializer[REQ])
               (handler: (REQ) => Future[ResponseBase]): Future[Subscription]

  def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
              groupName: String,
              inputDeserializer: RequestDeserializer[REQ],
              subscriber: Subscriber[REQ]): Future[Subscription]

  def off(subscription: Subscription): Future[Unit]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}
