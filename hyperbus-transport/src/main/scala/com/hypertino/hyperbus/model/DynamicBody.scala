package com.hypertino.hyperbus.model

import java.io.{Reader, Writer}

import com.hypertino.binders.json.JsonBindersFactory
import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.hyperbus.serialization.ResponseDeserializer

trait DynamicBody extends Body with DynamicBodyTrait {
  def content: Value

  def serialize(writer: Writer): Unit = {
    implicit val bindOptions = com.hypertino.hyperbus.serialization.bindOptions
    com.hypertino.binders.json.JsonBindersFactory.findFactory().withWriter(writer) { serializer =>
      serializer.bind[Value](content)
    }
  }

  def copy(
            contentType: Option[String] = this.contentType,
            content: Value = this.content
          ): DynamicBody = {
    DynamicBody(content, contentType)
  }
}

object DynamicBody {
  def apply(content: Value, contentType: Option[String]): DynamicBody = DynamicBodyContainer(contentType, content)

  def apply(content: Value): DynamicBody = DynamicBodyContainer(None, content)

  def apply(reader: Reader, contentType: Option[String]): DynamicBody = {
    implicit val bindOptions = com.hypertino.hyperbus.serialization.bindOptions
    JsonBindersFactory.findFactory().withReader(reader) { deserializer =>
      apply(deserializer.unbind[Value], contentType)
    }
  }

  def unapply(dynamicBody: DynamicBody) = Some((dynamicBody.contentType, dynamicBody.content))
}

private[model] case class DynamicBodyContainer(contentType: Option[String], content: Value) extends DynamicBody





