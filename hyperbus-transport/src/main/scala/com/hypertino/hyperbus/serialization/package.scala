package com.hypertino.hyperbus

import java.io.Reader

import com.hypertino.binders.core.BindOptions
import com.hypertino.hyperbus.model._

package object serialization {
  type MessageDeserializer[+T <: Message[Body, Headers]] = (Reader, HeadersMap) ⇒ T
  type RequestDeserializer[+T <: Request[Body]] = (Reader, HeadersMap) ⇒ T
  type ResponseDeserializer[+T <: Response[Body]] = (Reader, HeadersMap) ⇒ T
  type ResponseBodyDeserializer = (Reader, Option[String]) ⇒ Body
  type RequestBaseDeserializer = RequestDeserializer[RequestBase]
  type ResponseBaseDeserializer = ResponseDeserializer[ResponseBase]

  implicit val bindOptions = new BindOptions(true)
}
