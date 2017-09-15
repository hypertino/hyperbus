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
