package com.hypertino.hyperbus.model

import java.io.Reader

import com.hypertino.binders.json.JsonBindersFactory
import com.hypertino.binders.value._

trait EmptyBody extends DynamicBody

// todo: make empty body really empty!?
case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = None

  def content = Null

  def apply(reader: Reader, contentType: Option[String]): EmptyBody = {
    import com.hypertino.binders.json.JsonBinders._
    JsonBindersFactory.findFactory().withReader(reader) { deserializer =>
      deserializer.unbind[Value]
    }
    EmptyBody
  }
}