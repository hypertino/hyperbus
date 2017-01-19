package com.hypertino.hyperbus.transport.api

import com.hypertino.hyperbus.model.HeadersBuilder

object Headers {
  def apply(vargs: (String, Seq[String])*)
           (implicit mcx: com.hypertino.hyperbus.model.MessagingContext): Headers = {
    val builder = new HeadersBuilder()
    builder ++= vargs
    builder withContext mcx result()
  }

  def apply(map: Map[String, Seq[String]])
           (implicit mcx: com.hypertino.hyperbus.model.MessagingContext): Headers = {
    new HeadersBuilder(map) withContext mcx result()
  }

  def apply()(implicit mcx: com.hypertino.hyperbus.model.MessagingContext): Headers = {
    new HeadersBuilder() withContext mcx result()
  }

  def unapply(headers: Headers): Option[Map[String, Seq[String]]] = Some(headers)

  def plain(headers: Map[String, Seq[String]]): Headers = {
    new HeadersBuilder(headers) result()
  }
}
