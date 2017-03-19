package com.hypertino.hyperbus.model

import java.io.{Reader, Writer}

import com.hypertino.binders.value._

trait EmptyBody extends DynamicBody {
  override def serialize(writer: Writer): Unit = {}
  override def isEmpty = true
}

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = None
  def content = Null
  def apply(reader: Reader, contentType: Option[String]): EmptyBody = this
}
