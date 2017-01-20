package com.hypertino.hyperbus.serialization

import com.hypertino.binders.json.JsonBindersFactory
import com.hypertino.binders.value.{Null, Value}
import com.hypertino.inflector.naming.PlainConverter
import com.hypertino.hyperbus.model._

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

object StringDeserializer {
  def request[T <: Request[_]](input: String): T = macro StringDeserializerImpl.request[T]

  def dynamicRequest(input: String): DynamicRequest = {
    com.hypertino.hyperbus.serialization.MessageDeserializer.deserializeRequestWith[DynamicRequest](input)(DynamicRequest.apply)
  }

  def dynamicBody(content: Option[String]): DynamicBody = content match {
    case None ⇒ DynamicBody(Null)
    case Some(string) ⇒ {
      val value = JsonBindersFactory.findFactory().withStringParser(string) { case jsonParser ⇒ // dont remove this!
        jsonParser.unbind[Value]
      }
      DynamicBody(value)
    }
  }

  // todo: response for DefinedResponse

  def dynamicResponse(input: String): Response[DynamicBody] = {
    MessageDeserializer.deserializeResponseWith(input) { (responseHeader, responseBodyJson) =>
      StandardResponse(responseHeader, responseBodyJson).asInstanceOf[Response[DynamicBody]]
    }
  }
}

private[serialization] object StringDeserializerImpl {
  def request[T: c.WeakTypeTag](c: Context)(input: c.Expr[String]): c.Expr[T] = {
    import c.universe._
    val typ = weakTypeOf[T]
    val deserializer = typ.companion.decl(TermName("apply"))
    if (typ.companion == null) {
      c.abort(c.enclosingPosition, s"Can't find companion object for $typ (required to deserialize)")
    }
    c.Expr[T](
      q"""{
      com.hypertino.hyperbus.serialization.MessageDeserializer.deserializeRequestWith[$typ]($input)($deserializer)
    }""")
  }
}
