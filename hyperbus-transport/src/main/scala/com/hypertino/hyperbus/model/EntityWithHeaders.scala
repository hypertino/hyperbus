package com.hypertino.hyperbus.model

import com.hypertino.binders.value.Value

trait EntityWithHeaders {
  def headers: Headers

  def headerOption(name: String): Option[Value] = headers.get(name)

  def header(name: String): Value = headerOption(name).getOrElse(throw new NoSuchHeaderException(name))

  def messageId: String = header(Header.MESSAGE_ID).toString

  def correlationId: Option[String] = headerOption(Header.CORRELATION_ID).map(_.toString).orElse(Some(messageId))

  def contentType: Option[String] = headerOption(Header.CONTENT_TYPE).map(_.toString)
}
