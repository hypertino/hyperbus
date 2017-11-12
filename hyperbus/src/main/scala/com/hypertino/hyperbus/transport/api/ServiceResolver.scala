/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.model.{HRL, MessageBase, RequestBase}
import monix.eval.Task
import monix.reactive.Observable

trait ServiceResolver {
  def lookupService(message: RequestBase): Task[ServiceEndpoint] = {
    lookupServiceEndpoints(message).map(_.headOption.getOrElse{
      throw new NoTransportRouteException(s"Can't resolve: ${message.headers.hrl}")
    })
  }
  def lookupServiceEndpoints(message: RequestBase): Task[Seq[ServiceEndpoint]] = {
    serviceObservable(message).firstL
  }
  def serviceObservable(message: RequestBase): Observable[Seq[ServiceEndpoint]]
}
