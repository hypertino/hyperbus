/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.subscribe.{Subscribable, SubscribeMacro}
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.typesafe.scalalogging.Logger
import monix.eval.Task
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.experimental.macros

// todo: document API
trait HyperbusApi {
  def ask[REQ <: RequestBase, M <: RequestMeta[REQ]](request: REQ)(implicit requestMeta: M): Task[M#ResponseType]

  def publish[REQ <: RequestBase](request: REQ)(implicit requestMeta: RequestMeta[REQ]): Task[Seq[Any]]

  def commands[REQ <: RequestBase](implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[CommandEvent[REQ]]

  def commands(observableMeta: RequestObservableMeta[DynamicRequest]): Observable[CommandEvent[DynamicRequest]] = {
    commands(DynamicRequest.requestMeta, observableMeta)
  }

  def events[REQ <: RequestBase](groupName: Option[String])(implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[REQ]

  def events(groupName: Option[String], observableMeta: RequestObservableMeta[DynamicRequest]): Observable[DynamicRequest] = {
    events(groupName)(DynamicRequest.requestMeta, observableMeta)
  }

  def shutdown(duration: FiniteDuration): Task[Boolean]

  def subscribe[A](serviceClass: A, log: Logger): Seq[Cancelable] = macro SubscribeMacro.subscribeWithLog[A]

  def subscribe[A <: Subscribable](serviceClass: A): Seq[Cancelable] = macro SubscribeMacro.subscribe[A]

  def startServices(): Unit

  def safeHandleCommand[REQ <: RequestBase](command: CommandEvent[REQ], log: Option[Logger])(handler: REQ ⇒ Task[ResponseBase]): Future[Ack]

  def safeHandleEvent[REQ <: RequestBase](request: REQ)(handler: REQ ⇒ Future[Ack]): Future[Ack]
}
