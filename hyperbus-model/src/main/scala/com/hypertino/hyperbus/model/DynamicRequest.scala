package com.hypertino.hyperbus.model
import java.io.{Reader, Writer}

import com.hypertino.binders.json.JsonBindersFactory
import com.hypertino.binders.value.Value

trait DynamicBody extends Body with HalLinks {
  def content: Value

  lazy val links: Links = content.__links.to[Option[Links]].getOrElse(Map.empty)

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

case class DynamicRequest(body: DynamicBody,
                          headers: RequestHeaders) extends Request[DynamicBody]

object DynamicRequest extends RequestObjectApi[DynamicRequest] {
  override def serviceAddress: String = invalidOperation("serviceAddress")
  override def method: String = invalidOperation("serviceAddress")

  def apply(reader: Reader, headersMap: HeadersMap): DynamicRequest = {
    val headers = RequestHeaders(headersMap)
    val body = DynamicBody(reader, headers.contentType)
    new DynamicRequest(body, headers)
  }

  def apply(hri: HRI, method: String, body: DynamicBody, headers: HeadersMap)
           (implicit mcx: MessagingContext): DynamicRequest = {
    DynamicRequest(body, RequestHeaders(new HeadersBuilder(headers)
      .withHRI(hri)
      .withMethod(method)
      .withContentType(body.contentType)
      .withContext(mcx)
      .result())
    )
  }

  def apply(hri: HRI, method: String, body: DynamicBody)
           (implicit mcx: MessagingContext): DynamicRequest = {
    DynamicRequest(body, RequestHeaders(new HeadersBuilder()
      .withHRI(hri)
      .withMethod(method)
      .withContentType(body.contentType)
      .withContext(mcx)
      .result())
    )
  }

  def apply(body: DynamicBody, headers: HeadersMap): DynamicRequest = {
    DynamicRequest(body, RequestHeaders(headers))
  }

  private def invalidOperation(name: String): String = throw new UnsupportedOperationException(s"DynamicRequest.$name")
}
