/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher

trait TansportRoute[T] {
  def transport: T
  def matcher: RequestMatcher
}
case class ClientTransportRoute(transport: ClientTransport, matcher: RequestMatcher) extends TansportRoute[ClientTransport]
case class ServerTransportRoute(transport: ServerTransport, matcher: RequestMatcher, registrator: ServiceRegistrator) extends TansportRoute[ServerTransport]

