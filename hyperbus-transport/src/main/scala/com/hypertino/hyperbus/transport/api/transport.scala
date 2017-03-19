package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization.{RequestDeserializer, ResponseBaseDeserializer}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.eval.Task
import monix.execution.Cancelable
import monix.reactive.Observable

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

trait PublishResult {
  def sent: Option[Boolean]

  def offset: Option[String]
}

trait ClientTransport {
  def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Task[ResponseBase]

  def publish(message: RequestBase): Task[PublishResult]

  def shutdown(duration: FiniteDuration): Task[Boolean]
}

case class CommandEvent[REQ <: Request[Body]](request: REQ, responsePromise: Promise[ResponseBase]) extends MessagingContext {
  override def correlationId: Option[String] = request.correlationId
}

trait ServerTransport {
  def commands[REQ <: Request[Body]](matcher: RequestMatcher,
                                     inputDeserializer: RequestDeserializer[REQ]): Observable[CommandEvent[REQ]]

  def events[REQ <: Request[Body]](matcher: RequestMatcher,
                                   groupName: String,
                                   inputDeserializer: RequestDeserializer[REQ]): Observable[REQ]

  def shutdown(duration: FiniteDuration): Task[Boolean]
}

class NoTransportRouteException(message: String) extends RuntimeException(message)

