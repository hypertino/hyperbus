package com.hypertino.hyperbus.model

import com.hypertino.binders.value.{Lst, LstV, Text, Value}
import com.hypertino.hyperbus.transport.api.{Header, Headers}

import scala.collection.mutable


class HeadersBuilder(private[this] val mapBuilder: mutable.Builder[(String, Value), Map[String, Value]]) {
  def this() = this(Map.newBuilder[String, Value])

  def this(headers: Map[String, Value]) = this {
    Map.newBuilder[String, Value] ++= headers
  }

  def +=(kv: (String, Value)) = {
    mapBuilder += kv._1 → LstV(kv._2)
    this
  }

  def ++=(headers: Map[String, Value]) = {
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

  def result(): Headers = {
    mapBuilder.result().filterNot(_._2.isEmpty)
  }
}
