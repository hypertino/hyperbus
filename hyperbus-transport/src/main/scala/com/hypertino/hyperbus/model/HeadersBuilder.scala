package com.hypertino.hyperbus.model

import com.hypertino.binders.value._

import scala.collection.immutable.ListMap

class HeadersBuilder() {
  private[this] val mapBuilder = ListMap.newBuilder[String, Value]

  def +=(kv: (String, Value)): HeadersBuilder = {
    mapBuilder += kv._1 → kv._2
    this
  }

  def ++=(headers: Obj): HeadersBuilder = {
    mapBuilder ++= headers.v.toSeq.reverse
    this
  }

  def ++=(headers: Seq[(String, Value)]): HeadersBuilder = {
    mapBuilder ++= headers.reverse
    this
  }

  def withContext(mcx: com.hypertino.hyperbus.model.MessagingContext): HeadersBuilder = {
    val messageId = mcx.createMessageId()
    withMessageId(mcx.createMessageId())
    if (mcx != MessagingContext.empty) {
      val correlationId = mcx.correlationId
      if (messageId != correlationId) {
        mapBuilder += Header.CORRELATION_ID → Text(correlationId)
      }
    }
    this
  }

  def withMessageId(messageId: String): HeadersBuilder = {
    mapBuilder += Header.MESSAGE_ID → Text(messageId)
    this
  }

  def withContentType(contentType: Option[String]): HeadersBuilder = {
    mapBuilder ++= contentType.map(ct => Header.CONTENT_TYPE → Text(ct))
    this
  }

  def withMethod(method: String): HeadersBuilder = {
    mapBuilder += Header.METHOD → Text(method)
    this
  }

  def withHRL(hrl: HRL): HeadersBuilder = {
    implicit val bindOptions = com.hypertino.hyperbus.serialization.bindOptions
    mapBuilder += Header.HRL → hrl.toValue
    this
  }

  def withStatusCode(statusCode: Int): HeadersBuilder = {
    mapBuilder += Header.STATUS_CODE → statusCode
    this
  }

  def result(): HeadersMap = {
    mapBuilder
      .result()
      .filterNot(_._2.isEmpty) // todo: move this to the stage of building?
  }

  def requestHeaders(): RequestHeaders = RequestHeaders(result())

  def responseHeaders(): ResponseHeaders = ResponseHeaders(result())
}
