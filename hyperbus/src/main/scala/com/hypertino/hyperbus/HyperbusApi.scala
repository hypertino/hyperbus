package com.hypertino.hyperbus

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.eval.Task
import monix.reactive.Observable

import scala.concurrent.duration.FiniteDuration
import scala.language.experimental.macros

// todo: document API
trait HyperbusApi {
  def <~[REQ <: RequestBase](request: REQ)(implicit requestMeta: RequestMeta[REQ]): Task[requestMeta.ResponseType]

  def <|[REQ <: RequestBase](request: REQ)(implicit requestMeta: RequestMeta[REQ]): Task[PublishResult]

  def ~>[REQ <: RequestBase](implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[CommandEvent[REQ]] = {
    commands(observableMeta.requestMatcher)
  }

  def |>[REQ <: RequestBase](groupName: Option[String])(implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[REQ] = {
    events(observableMeta.requestMatcher,groupName)
  }

  def |>[REQ <: DynamicRequest](groupName: Option[String], observableMeta: RequestObservableMeta[REQ]): Observable[DynamicRequest] = {
    events(observableMeta.requestMatcher, groupName)(DynamicRequest.requestMeta)
  }

  def commands[REQ <: RequestBase](requestMatcher: RequestMatcher)(implicit requestMeta: RequestMeta[REQ]): Observable[CommandEvent[REQ]]

  def events[REQ <: RequestBase](requestMatcher: RequestMatcher, groupName: Option[String])(implicit requestMeta: RequestMeta[REQ]): Observable[REQ]

  def shutdown(duration: FiniteDuration): Task[Boolean]
}
