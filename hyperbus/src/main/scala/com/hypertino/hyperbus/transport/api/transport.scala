package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization.{RequestDeserializer, ResponseBaseDeserializer}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.eval.{Callback, Task}
import monix.execution.Cancelable
import monix.reactive.Observable

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

trait PublishResult {
  def sent: Option[Boolean]

  def offset: Option[String]
}

trait ClientTransport {
  def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Task[ResponseBase]

  def publish(message: RequestBase): Task[PublishResult]

  def shutdown(duration: FiniteDuration): Task[Boolean]
}

case class CommandEvent[+REQ <: RequestBase](request: REQ, reply: Callback[ResponseBase]) extends MessagingContext {
  override def correlationId: String = request.correlationId
  override def parentId = request.parentId
}

trait ServerTransport {
  def commands[REQ <: RequestBase](matcher: RequestMatcher,
                                     inputDeserializer: RequestDeserializer[REQ]): Observable[CommandEvent[REQ]]

  def events[REQ <: RequestBase](matcher: RequestMatcher,
                                   groupName: String,
                                   inputDeserializer: RequestDeserializer[REQ]): Observable[REQ]

  def shutdown(duration: FiniteDuration): Task[Boolean]
}

class NoTransportRouteException(message: String) extends RuntimeException(message)

