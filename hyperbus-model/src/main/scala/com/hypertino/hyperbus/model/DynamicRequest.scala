package com.hypertino.hyperbus.model

import java.io.{Reader, Writer}

import com.hypertino.binders.json.JsonBindersFactory
import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.hyperbus.serialization.ResponseDeserializer

case class DynamicRequest(body: DynamicBody,
                          headers: RequestHeaders) extends Request[DynamicBody]

case class DynamicRequestObservableMeta(serviceAddress: String, method: String, contentType: Option[String])
  extends RequestObservableMeta[DynamicRequest]

object DynamicRequest extends RequestMeta[DynamicRequest] {
  type ResponseType = DynamicResponse
  implicit val requestMeta: RequestMeta[DynamicRequest] = this

  def apply(hri: HRI, method: String, body: DynamicBody, headersObj: Obj)
           (implicit mcx: MessagingContext): DynamicRequest = {
    DynamicRequest(body, RequestHeaders(new HeadersBuilder()
      .withHRI(hri)
      .withMethod(method)
      .withContentType(body.contentType)
      .withContext(mcx)
      .++=(headersObj)
      .result())
    )
  }

  def apply(hri: HRI, method: String, body: DynamicBody)
           (implicit mcx: MessagingContext): DynamicRequest = {
    DynamicRequest(body, RequestHeaders(new HeadersBuilder()
      .withHRI(hri)
      .withMethod(method)
      .withContentType(body.contentType)
      .withContext(mcx)
      .result())
    )
  }

  def apply(reader: Reader, headersObj: Obj): DynamicRequest = {
    val headers = RequestHeaders(headersObj)
    val body = DynamicBody(reader, headers.contentType)
    new DynamicRequest(body, headers)
  }

  override def responseDeserializer: ResponseDeserializer[DynamicResponse] = StandardResponse.dynamicDeserializer
}
