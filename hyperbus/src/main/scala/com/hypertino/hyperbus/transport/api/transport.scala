/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

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
  def committed: Option[Boolean]
  def offset: Option[String]
}

trait ClientTransport {
  def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Task[ResponseBase]
  def publish(message: RequestBase): Task[PublishResult]
  def shutdown(duration: FiniteDuration): Task[Boolean]
}

case class CommandEvent[+REQ <: RequestBase](request: REQ, reply: Callback[ResponseBase]) extends MessagingContext {
  override def correlationId: String = request.correlationId
  override def parentId: Option[String] = request.parentId
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

object PublishResult {
  val empty: PublishResult = new PublishResult {
    override def offset: Option[String] = None
    override def committed: Option[Boolean] = None
    override def toString: String = "PublishResult.empty"
  }
  val committed: PublishResult = new PublishResult {
    override def offset: Option[String] = None
    override def committed: Option[Boolean] = Some(true)
    override def toString: String = "PublishResult.sent"
  }
}