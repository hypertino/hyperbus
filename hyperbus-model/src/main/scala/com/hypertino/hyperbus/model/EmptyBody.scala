package com.hypertino.hyperbus.model

import java.io.{Reader, Writer}

import com.hypertino.binders.value._
import com.hypertino.hyperbus.serialization.DeserializeException

trait EmptyBody extends DynamicBody {
  override def serialize(writer: Writer): Unit = writer.write("{}")
  override def isEmpty = true
}

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = None
  def content = Null
  def apply(reader: Reader, contentType: Option[String]): EmptyBody = {
    val body = DynamicBody.apply(reader, contentType)
    if (body.content.isEmpty)
      EmptyBody
    else {
      throw DeserializeException(s"EmptyBody is expected, but got: '${body.content}'")
    }
  }
}
