package com.hypertino.hyperbus

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.subscribe.{Subscribable, SubscribeMacro}
import com.hypertino.hyperbus.transport.api._
import com.typesafe.scalalogging.Logger
import monix.eval.Task
import monix.execution.Cancelable
import monix.reactive.Observable

import scala.concurrent.duration.FiniteDuration
import scala.language.experimental.macros

// todo: document API
trait HyperbusApi {
  def ask[REQ <: RequestBase, M <: RequestMeta[REQ]](request: REQ)(implicit requestMeta: M): Task[M#ResponseType]

  def publish[REQ <: RequestBase](request: REQ)(implicit requestMeta: RequestMeta[REQ]): Task[PublishResult]

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
}
