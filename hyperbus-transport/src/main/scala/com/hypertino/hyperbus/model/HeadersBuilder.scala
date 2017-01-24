package com.hypertino.hyperbus.model

import com.hypertino.binders.value.{LstV, Text, Value}

import scala.collection.mutable

class HeadersBuilder(private[this] val mapBuilder: mutable.Builder[(String, Value), HeadersMap]) {
  def this() = this(Map.newBuilder[String, Value])

  def this(headers: HeadersMap) = this {
    Map.newBuilder[String, Value] ++= headers
  }

  def +=(kv: (String, Value)) = {
    mapBuilder += kv._1 → LstV(kv._2)
    this
  }

  def ++=(headers: HeadersMap) = {
    mapBuilder ++= headers
    this
  }

  def ++=(headers: Seq[(String, Value)]) = {
    mapBuilder ++= headers
    this
  }

  def withCorrelation(correlationId: Option[String]) = {
    mapBuilder ++= correlationId.map(c ⇒ Header.CORRELATION_ID → Text(c))
    this
  }

  def withContext(mcx: com.hypertino.hyperbus.model.MessagingContext) = {
    withMessageId(mcx.createMessageId())
    withCorrelation(mcx.correlationId)
  }

  def withMessageId(messageId: String): HeadersBuilder = {
    mapBuilder += Header.MESSAGE_ID → Text(messageId)
    this
  }

  def withContentType(contentType: Option[String]): HeadersBuilder = {
    mapBuilder ++= contentType.map(ct => Header.CONTENT_TYPE → Text(ct))
    this
  }

  def withMethod(method: String) = {
    mapBuilder += Header.METHOD → Text(method)
    this
  }

  def withUri(uri: String) = {
    mapBuilder += Header.URI → Text(uri)
    this
  }

  def result(): HeadersMap = {
    mapBuilder.result().filterNot(_._2.isEmpty)
  }
}
