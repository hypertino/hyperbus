package com.hypertino.hyperbus.model

import com.hypertino.binders.value._

class HeadersBuilder(private[this] val mapBuilder: scala.collection.mutable.LinkedHashMap[String, Value]) {
  def this() = this(scala.collection.mutable.LinkedHashMap[String, Value]())

  def +=(kv: (String, Value)): HeadersBuilder = {
    mapBuilder += kv._1 → kv._2
    this
  }

  def ++=(headers: Obj): HeadersBuilder = {
    mapBuilder ++= headers.v
    this
  }

  def ++=(headers: Seq[(String, Value)]): HeadersBuilder = {
    mapBuilder ++= headers
    this
  }

  def withCorrelation(correlationId: Option[String]): HeadersBuilder = {
    mapBuilder ++= correlationId.map(c ⇒ Header.CORRELATION_ID → Text(c))
    this
  }

  def withContext(mcx: com.hypertino.hyperbus.model.MessagingContext): HeadersBuilder = {
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

  def withMethod(method: String): HeadersBuilder = {
    mapBuilder += Header.METHOD → Text(method)
    this
  }

  def withHRI(hri: HRI): HeadersBuilder = {
    mapBuilder += Header.HRI → hri.toValue
    this
  }

  def result(): Obj = {
    new Obj(mapBuilder.filterNot(_._2.isEmpty))
  }
}
