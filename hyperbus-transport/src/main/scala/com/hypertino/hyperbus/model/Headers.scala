package com.hypertino.hyperbus.model

import java.io.Writer
import java.net.URI

import com.hypertino.binders.value.Value

trait Headers {
  def all : HeadersMap

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
  def hri: HRI = all.safe(Header.HRI).to[HRI]

  def method: String = all.safe(Header.METHOD).toString
}

object RequestHeaders {
  val empty = RequestHeaders(Map.empty)
}

case class ResponseHeaders(all: HeadersMap) extends Headers {
  lazy val statusCode: Int = all(Header.STATUS_CODE).toInt
}

object ResponseHeaders {
  val empty = ResponseHeaders(Map.empty)
}

