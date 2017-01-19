package com.hypertino.hyperbus.model

import java.io._

import com.fasterxml.jackson.core.JsonParser
import com.hypertino.binders.json.JsonBindersFactory
import com.hypertino.binders.value._
import com.hypertino.hyperbus.serialization.{MessageDeserializer, RequestHeader}
import com.hypertino.hyperbus.transport.api.uri.Uri

trait DynamicBody extends Body with Links {
  def content: Value

  lazy val links: Links.LinksMap = content.__links.to[Option[Links.LinksMap]].getOrElse(Map.empty)

  def serialize(writer: Writer): Unit = {
    import com.hypertino.binders._
    com.hypertino.binders.json.JsonBindersFactory.findFactory().withWriter(writer) { serializer =>
      serializer.bind[Value](content)
    }
  }

  def copy(
            contentType: Option[String] = this.contentType,
            content: Value = this.content
          ): DynamicBody = {
    DynamicBody(contentType, content)
  }
}

object DynamicBody {
  def apply(contentType: Option[String], content: Value): DynamicBody = DynamicBodyContainer(contentType, content)

  def apply(content: Value): DynamicBody = DynamicBodyContainer(None, content)

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): DynamicBody = {
    import com.hypertino.binders.json.JsonBinders._
    JsonBindersFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      apply(contentType, deserializer.unbind[Value])
    }
  }

  def unapply(dynamicBody: DynamicBody) = Some((dynamicBody.contentType, dynamicBody.content))
}

private[model] case class DynamicBodyContainer(contentType: Option[String], content: Value) extends DynamicBody

case class DynamicRequest(uri: Uri,
                          body: DynamicBody,
                          headers: Headers) extends Request[DynamicBody]

object DynamicRequest {
  def apply(requestHeader: RequestHeader, jsonParser: JsonParser): DynamicRequest = {
    val b = DynamicBody(requestHeader.contentType, jsonParser)
    apply(requestHeader, b)
  }

  def apply(message: String): DynamicRequest = {
    val stringReader = new StringReader(message)
    try {
      apply(stringReader)
    }
    finally {
      stringReader.close()
    }
  }

  def apply(reader: Reader): DynamicRequest = {
    MessageDeserializer.deserializeRequestWith(reader) { (requestHeader, jsonParser) ⇒
      DynamicRequest(requestHeader, jsonParser)
    }
  }

  def apply(requestHeader: RequestHeader, body: DynamicBody): DynamicRequest = {
    DynamicRequest(requestHeader.uri, body, Headers.plain(requestHeader.headers))
  }

  def apply(uri: Uri, method: String, body: DynamicBody, headers: Headers): DynamicRequest = {
    DynamicRequest(uri, body, new HeadersBuilder(headers)
      .withMethod(method)
      .withContentType(body.contentType)
      .result())
  }

  def apply(uri: Uri, method: String, body: DynamicBody)
           (implicit mcx: MessagingContext): DynamicRequest = {
    apply(uri, method, body, Headers()(mcx))
  }
}
