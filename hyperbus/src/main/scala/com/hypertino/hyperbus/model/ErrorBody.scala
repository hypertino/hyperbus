package com.hypertino.hyperbus.model

import java.io.{Reader, Writer}

import com.hypertino.binders.core.BindOptions
import com.hypertino.binders.json.JsonBindersFactory
import com.hypertino.binders.value._
import com.hypertino.hyperbus.util.SeqGenerator

trait ErrorBody extends DynamicBody {
  def code: String

  def description: Option[String]

  def errorId: String

  def extra: Value

  def message: String

  def content = Obj(Map[String,Value](
      "code" → code,
      "errorId" → errorId
    )
      ++ description.map(s ⇒ "description" → Text(s))
      ++ contentType.map(s ⇒ "contentType" → Text(s))
      ++ {if(extra.isDefined) Some("extra" → extra) else None}
  )

  def copyErrorBody(
           code: String = this.code,
           description: Option[String] = this.description,
           errorId: String = this.errorId,
           extra: Value = this.extra,
           message: String = this.message,
           contentType: Option[String] = this.contentType
          ): ErrorBody = {
    ErrorBodyContainer(code, description, errorId, extra, contentType)
  }
}

object ErrorBody {
  def apply(code: String,
            description: Option[String] = None,
            errorId: String = SeqGenerator.create(),
            extra: Value = Null,
            contentType: Option[String] = None): ErrorBody =
    ErrorBodyContainer(code, description, errorId, extra, contentType)

  def unapply(errorBody: ErrorBody) = Some(
    (errorBody.code, errorBody.description, errorBody.errorId, errorBody.extra, errorBody.contentType)
  )

  def apply(reader: Reader, contentType: Option[String]): DynamicBody = {
    JsonBindersFactory.findFactory().withReader(reader) { deserializer =>
      deserializer.unbind[ErrorBodyContainer].copyErrorBody(contentType = contentType)
    }
  }
}

private[model] case class ErrorBodyContainer(code: String,
                                             description: Option[String],
                                             errorId: String,
                                             extra: Value,
                                             contentType: Option[String]) extends ErrorBody {

  def message = code + description.map(": " + _).getOrElse("") + ". #" + errorId

  override def serialize(writer: Writer)(implicit bindOptions: BindOptions): Unit = {
    JsonBindersFactory.findFactory().withWriter(writer) { serializer =>
      serializer.bind(this.copyErrorBody(contentType = None)) // find other way to skip contentType
    }
  }
}
