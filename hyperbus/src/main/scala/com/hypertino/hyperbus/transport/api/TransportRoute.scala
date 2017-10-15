package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher

trait TansportRoute[T] {
  def transport: T
  def matcher: RequestMatcher
}
case class ClientTransportRoute(transport: ClientTransport, matcher: RequestMatcher) extends TansportRoute[ClientTransport]
case class ServerTransportRoute(transport: ServerTransport, matcher: RequestMatcher, registrator: ServiceRegistrator) extends TansportRoute[ServerTransport]

