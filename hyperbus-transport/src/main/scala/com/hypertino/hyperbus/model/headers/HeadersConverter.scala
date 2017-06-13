package com.hypertino.hyperbus.model.headers

import com.hypertino.binders.value.Obj

trait HeadersConverter {
  def fromHttp(headers: Seq[(String, String)]): Obj
  def toHttp(headers: Obj): Seq[(String, String)]
}
