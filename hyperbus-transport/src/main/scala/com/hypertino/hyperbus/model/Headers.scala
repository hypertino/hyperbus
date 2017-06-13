package com.hypertino.hyperbus.model

import java.io.Writer

import com.hypertino.binders.value.{Obj, Value}

trait Headers {
  def all : Obj

  def messageId: String = all.safe(Header.MESSAGE_ID).toString

  def correlationId: Option[String] = all.v.get(Header.CORRELATION_ID).map(_.toString).orElse(Some(messageId))

  def contentType: Option[String] = all.v.get(Header.CONTENT_TYPE).map(_.toString)

  def serialize(writer: Writer) : Unit = {
    import com.hypertino.binders.json.JsonBinders._
    implicit val bindOptions = com.hypertino.hyperbus.serialization.bindOptions
    all.writeJson(writer)
  }
}

case class RequestHeaders(all: Obj) extends Headers {
  def hrl: HRL = all.safe(Header.HRL).to[HRL]

  def method: String = all.safe(Header.METHOD).toString
}

object RequestHeaders {
  val empty = RequestHeaders(Obj.empty)
}

case class ResponseHeaders(all: Obj) extends Headers {
  lazy val statusCode: Int = all.v(Header.STATUS_CODE).toInt

  def location: HRL = all.v(Header.LOCATION).to[HRL]
}

object ResponseHeaders {
  val empty = ResponseHeaders(Obj.empty)
}

