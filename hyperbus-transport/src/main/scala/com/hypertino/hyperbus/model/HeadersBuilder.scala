package com.hypertino.hyperbus.model

import com.hypertino.hyperbus.transport.api.{Header, Headers}

import scala.collection.mutable


class HeadersBuilder(private[this] val mapBuilder: mutable.Builder[(String, Seq[String]), Map[String, Seq[String]]]) {
  def this() = this(Map.newBuilder[String, Seq[String]])

  def this(headers: Map[String, Seq[String]]) = this {
    Map.newBuilder[String, Seq[String]] ++= headers
  }

  def +=(kv: (String, String)) = {
    mapBuilder += kv._1 → Seq(kv._2)
    this
  }

  def ++=(headers: Map[String, Seq[String]]) = {
    mapBuilder ++= headers
    this
  }

  def ++=(headers: Seq[(String, Seq[String])]) = {
    mapBuilder ++= headers
    this
  }

  def withCorrelation(correlationId: Option[String]) = {
    mapBuilder ++= correlationId.map(c ⇒ Header.CORRELATION_ID → Seq(c))
    this
  }

  def withContext(mcx: com.hypertino.hyperbus.model.MessagingContext) = {
    withMessageId(mcx.createMessageId())
    withCorrelation(mcx.correlationId)
  }

  def withMessageId(messageId: String): HeadersBuilder = {
    mapBuilder += Header.MESSAGE_ID → Seq(messageId)
    this
  }

  def withContentType(contentType: Option[String]): HeadersBuilder = {
    mapBuilder ++= contentType.map(ct => Header.CONTENT_TYPE → Seq(ct))
    this
  }

  def withMethod(method: String) = {
    mapBuilder += Header.METHOD → Seq(method)
    this
  }

  def result(): Headers = {
    mapBuilder.result().filterNot(_._2.isEmpty)
  }
}
