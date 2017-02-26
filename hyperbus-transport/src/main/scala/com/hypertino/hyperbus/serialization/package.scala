package com.hypertino.hyperbus

import java.io.Reader

import com.hypertino.binders.core.BindOptions
import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model._

package object serialization {
  type MessageDeserializer[+T <: Message[Body, Headers]] = (Reader, Obj) ⇒ T
  type RequestDeserializer[+T <: Request[Body]] = (Reader, Obj) ⇒ T
  type ResponseDeserializer[+T <: Response[Body]] = (Reader, Obj) ⇒ T
  type ResponseBodyDeserializer = (Reader, Option[String]) ⇒ Body
  type RequestBaseDeserializer = RequestDeserializer[RequestBase]
  type ResponseBaseDeserializer = ResponseDeserializer[ResponseBase]

  implicit val bindOptions = new BindOptions(true)
}
