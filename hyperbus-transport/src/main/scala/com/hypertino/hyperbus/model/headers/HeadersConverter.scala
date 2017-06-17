package com.hypertino.hyperbus.model.headers

import com.hypertino.hyperbus.model.HeadersMap

trait HeadersConverter {
  def fromHttp(headers: Seq[(String, String)]): HeadersMap
  def toHttp(headers: HeadersMap): Seq[(String, String)]
}
