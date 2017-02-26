package com.hypertino.hyperbus.model

import com.hypertino.binders.value._

class HeadersBuilder(private[this] val mapBuilder: scala.collection.mutable.LinkedHashMap[String, Value]) {
  def this() = this(scala.collection.mutable.LinkedHashMap[String, Value]())

  def this(headers: HeadersMap) = this {
    scala.collection.mutable.LinkedHashMap() ++= headers
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

  def withHRI(hri: HRI) = {
    mapBuilder += Header.HRI → hri.toValue
    this
  }

  def result(): HeadersMap = {
    mapBuilder.filterNot(_._2.isEmpty).toMap
  }
}
