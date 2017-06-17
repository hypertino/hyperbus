package com.hypertino.hyperbus.model

import java.io.Writer

import com.hypertino.binders.value.Value

object HeadersMap {
  val empty: scala.collection.Map[String,Value] = Map.empty[String,Value]
}

trait Headers {
  def all: HeadersMap

  def messageId: String = all.safe(Header.MESSAGE_ID).toString

  def correlationId: Option[String] = all.get(Header.CORRELATION_ID).map(_.toString).orElse(Some(messageId))

  def contentType: Option[String] = all.get(Header.CONTENT_TYPE).map(_.toString)

  def serialize(writer: Writer) : Unit = {
    import com.hypertino.binders.json.JsonBinders._
    implicit val bindOptions = com.hypertino.hyperbus.serialization.bindOptions
    all.writeJson(writer)
  }
}

case class RequestHeaders(all: HeadersMap) extends Headers {
  def hrl: HRL = all.safe(Header.HRL).to[HRL]

  def method: String = all.safe(Header.METHOD).toString
}

object RequestHeaders {
  val empty = RequestHeaders(HeadersMap.empty)
}

case class ResponseHeaders(all: HeadersMap) extends Headers {
  lazy val statusCode: Int = all(Header.STATUS_CODE).toInt

  def location: HRL = all(Header.LOCATION).to[HRL]
}

object ResponseHeaders {
  val empty = ResponseHeaders(HeadersMap.empty)
}

