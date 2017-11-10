package com.hypertino.hyperbus.model

import java.io.{Reader, Writer}

import com.hypertino.binders.json.JsonBindersFactory
import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.hyperbus.serialization.{ResponseDeserializer, SerializationOptions}

trait DynamicBody extends Body {
  def content: Value

  def serialize(writer: Writer)(implicit so: SerializationOptions): Unit = {
    import com.hypertino.binders.json.JsonBinders._
    import so._
    content.writeJson(writer)
  }

  def copy(
            content: Value = this.content,
            contentType: Option[String] = this.contentType
          ): DynamicBody = {
    DynamicBody(content, contentType)
  }
}

object DynamicBody {
  def apply(content: Value, contentType: Option[String]): DynamicBody = DynamicBodyContainer(content,contentType)

  def apply(content: Value): DynamicBody = DynamicBodyContainer(content,None)

  def apply(reader: Reader, contentType: Option[String]): DynamicBody = {
    JsonBindersFactory.findFactory().withReader(reader) { deserializer =>
      apply(deserializer.unbind[Value], contentType)
    }
  }

  def unapply(dynamicBody: DynamicBody) = Some((dynamicBody.content, dynamicBody.contentType))
}

private[model] case class DynamicBodyContainer(content: Value, contentType: Option[String]) extends DynamicBody






