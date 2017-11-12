/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus

import java.io.Reader

import com.hypertino.hyperbus.model._

package object serialization {
  type MessageDeserializer[+T <: Message[Body, MessageHeaders]] = (Reader, Headers) ⇒ T
  type RequestDeserializer[+T <: RequestBase] = (Reader, Headers) ⇒ T
  type ResponseDeserializer[+T <: ResponseBase] = (Reader, Headers) ⇒ T
  type ResponseBodyDeserializer = (Reader, Option[String]) ⇒ Body
  type RequestBaseDeserializer = RequestDeserializer[RequestBase]
  type ResponseBaseDeserializer = ResponseDeserializer[ResponseBase]
}
