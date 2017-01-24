package com.hypertino.hyperbus.model

import java.io.Writer
import java.net.URI

import com.hypertino.binders.value.Value

trait Headers {
  // todo: !!! rename this !!!
  def map : HeadersMap

  def stringHeaderOption(name: String): Option[String] = map.get(name).map(_.toString)

  def stringHeader(name: String): String = stringHeaderOption(name).getOrElse(throw new NoSuchHeaderException(name))

  def messageId: String = stringHeader(Header.MESSAGE_ID)

  def correlationId: Option[String] = stringHeaderOption(Header.CORRELATION_ID).orElse(Some(messageId))

  def contentType: Option[String] = stringHeaderOption(Header.CONTENT_TYPE)

  def serialize(writer: Writer) : Unit = {
    import com.hypertino.binders.json.JsonBinders._
    implicit val bindOptions = com.hypertino.hyperbus.serialization.bindOptions
    map.writeJson(writer)
  }
}

case class RequestHeaders(map: HeadersMap) extends Headers {
  lazy val parsedUri: URI = new URI(uri)

  def uri: String = stringHeader(Header.URI)

  def method: String = stringHeader(Header.METHOD)
}

object RequestHeaders {
  val empty = RequestHeaders(Map.empty)
}

case class ResponseHeaders(map: HeadersMap) extends Headers {
  lazy val statusCode: Int = map(Header.STATUS_CODE).toInt
}

object ResponseHeaders {
  val empty = ResponseHeaders(Map.empty)
}

