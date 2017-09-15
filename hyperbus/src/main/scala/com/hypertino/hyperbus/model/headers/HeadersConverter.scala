package com.hypertino.hyperbus.model.headers

import com.hypertino.hyperbus.model.Headers

trait HeadersConverter {
  def fromHttp(headers: Seq[(String, String)]): Headers
  def toHttp(headers: Headers): Seq[(String, String)]
}
