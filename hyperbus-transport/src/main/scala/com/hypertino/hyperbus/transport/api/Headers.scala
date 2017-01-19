package com.hypertino.hyperbus.transport.api

import com.hypertino.binders.value.Value
import com.hypertino.hyperbus.model.HeadersBuilder

object Headers {
  def apply(vargs: (String, Value)*)
           (implicit mcx: com.hypertino.hyperbus.model.MessagingContext): Headers = {
    val builder = new HeadersBuilder()
    builder ++= vargs
    builder withContext mcx result()
  }

  def apply(map: Map[String, Value])
           (implicit mcx: com.hypertino.hyperbus.model.MessagingContext): Headers = {
    new HeadersBuilder(map) withContext mcx result()
  }

  def apply()(implicit mcx: com.hypertino.hyperbus.model.MessagingContext): Headers = {
    new HeadersBuilder() withContext mcx result()
  }

  def unapply(headers: Headers): Option[Map[String, Value]] = Some(headers)

  def plain(headers: Map[String, Value]): Headers = {
    new HeadersBuilder(headers) result()
  }
}
