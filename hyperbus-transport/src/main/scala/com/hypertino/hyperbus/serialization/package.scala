package com.hypertino.hyperbus

import com.fasterxml.jackson.core.JsonParser
import com.hypertino.hyperbus.model._

package object serialization {
  type RequestDeserializer[+T <: Request[Body]] = (RequestHeader, JsonParser) ⇒ T
  type ResponseDeserializer[+T <: Response[Body]] = (ResponseHeader, JsonParser) ⇒ T
  type ResponseBodyDeserializer = (Option[String], JsonParser) ⇒ Body
  type RequestBaseDeserializer = RequestDeserializer[RequestBase]
  type ResponseBaseDeserializer = ResponseDeserializer[ResponseBase]
}
