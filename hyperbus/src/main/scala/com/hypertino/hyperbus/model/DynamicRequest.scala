package com.hypertino.hyperbus.model

import java.io.{Reader, Writer}

import com.hypertino.binders.json.JsonBindersFactory
import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.hyperbus.serialization.ResponseDeserializer
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher

case class DynamicRequest(body: DynamicBody,
                          headers: RequestHeaders) extends Request[DynamicBody]

case class DynamicRequestObservableMeta(requestMatcher: RequestMatcher)
  extends RequestObservableMeta[DynamicRequest]

object DynamicRequest extends RequestMeta[DynamicRequest] {
  type ResponseType = DynamicResponse
  implicit val requestMeta = this

  def apply(hrl: HRL, method: String, body: DynamicBody, headers: Headers)
           (implicit mcx: MessagingContext): DynamicRequest = {
    DynamicRequest(body, RequestHeaders(new HeadersBuilder()
      .withHRL(hrl)
      .withMethod(method)
      .withContentType(body.contentType)
      .withContext(mcx)
      .++=(headers)
      .result())
    )
  }

  def apply(hrl: HRL, method: String, body: DynamicBody)
           (implicit mcx: MessagingContext): DynamicRequest = {
    DynamicRequest(body, RequestHeaders(new HeadersBuilder()
      .withHRL(hrl)
      .withMethod(method)
      .withContentType(body.contentType)
      .withContext(mcx)
      .result())
    )
  }

  def apply(reader: Reader, headers: Headers): DynamicRequest = {
    val messageHeaders = RequestHeaders(headers)
    val body = DynamicBody(reader, messageHeaders.contentType)
    new DynamicRequest(body, messageHeaders)
  }

  override def responseDeserializer: ResponseDeserializer[DynamicResponse] = StandardResponse.dynamicDeserializer
}
