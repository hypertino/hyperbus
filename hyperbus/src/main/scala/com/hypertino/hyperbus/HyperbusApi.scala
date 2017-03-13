package com.hypertino.hyperbus

import com.hypertino.hyperbus.impl.MacroApi
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import rx.lang.scala.Observer

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.experimental.macros

// todo: document API
trait HyperbusApi {
  def <~[REQ <: Request[Body]](request: REQ): Any = macro HyperbusMacro.ask[REQ]

  def <|[REQ <: Request[Body]](request: REQ): Future[PublishResult] = macro HyperbusMacro.publish[REQ]

  def |>[REQ <: Request[Body]](observer: Observer[REQ]) : Future[HyperbusSubscription] = macro HyperbusMacro.onEvent[REQ]

  def ~>[REQ <: Request[Body]](handler: REQ => Future[Response[Body]]): Future[HyperbusSubscription] = macro HyperbusMacro.onCommand[REQ]

  def <~(request: DynamicRequest): Future[Response[DynamicBody]] = {
    ask[Response[DynamicBody],DynamicRequest](request,
      macroApiImpl.responseDeserializer(_, _, PartialFunction.empty).asInstanceOf[Response[DynamicBody]]
    )
  }

  def <|(request: DynamicRequest): Future[PublishResult] = {
    publish(request)
  }

  def ask[RESP <: Response[Body], REQ <: Request[Body]](request: REQ,
                                                        responseDeserializer: ResponseDeserializer[RESP]): Future[RESP]

  def publish[REQ <: Request[Body]](request: REQ): Future[PublishResult]

  def onCommand[RESP <: Response[Body], REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                                              requestDeserializer: RequestDeserializer[REQ])
                                                             (handler: (REQ) => Future[RESP]): Future[HyperbusSubscription]

  def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                    groupName: Option[String],
                                    requestDeserializer: RequestDeserializer[REQ],
                                    observer: Observer[REQ]): Future[HyperbusSubscription]

  def onEventForGroup[REQ <: Request[Body]](groupName: String,
                                            observer: Observer[REQ]): Future[HyperbusSubscription] = macro HyperbusMacro.onEventForGroup[REQ]

  def off(subscription: HyperbusSubscription): Future[Unit]

  def shutdown(duration: FiniteDuration): Future[Boolean]

  def macroApiImpl: MacroApi
}
