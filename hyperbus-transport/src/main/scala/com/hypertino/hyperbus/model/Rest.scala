package com.hypertino.hyperbus.model

import java.io.{Reader, StringWriter, Writer}

import com.hypertino.hyperbus.serialization.{MessageReader, RequestDeserializer, ResponseDeserializer}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher

trait Body {
  def isEmpty: Boolean = false
  def contentType: Option[String]
  def serialize(writer: Writer)
}

trait BodyObjectApi[B <: Body] {
  def contentType: Option[String]
  def apply(reader: Reader, contentType: Option[String]): B
}

trait NoContentType {
  def contentType: Option[String] = None
}

trait DynamicBodyTrait

trait Message[+B <: Body, +H <: Headers] {
  def headers: H

  def body: B

  def serialize(writer: Writer): Unit = {
    headers.serialize(writer)
    if (!body.isEmpty) {
      writer.write("\r\n")
      body.serialize(writer)
    }
  }

  def serializeToString: String = {
    val writer = new StringWriter()
    try {
      serialize(writer)
      writer.toString
    }
    finally {
      writer.close()
    }
  }

  override def toString: String = {
    s"${getClass.getName}[${body.getClass.getName}]:$serializeToString"
  }
}

trait Request[+B <: Body] extends Message[B, RequestHeaders] with MessagingContext {
  override def correlationId: Option[String] = headers.correlationId
}

trait RequestObservableMeta[R <: RequestBase] {
  def requestMatcher: RequestMatcher
}

trait RequestMeta[R <: RequestBase] {
  type ResponseType <: ResponseBase
  def responseDeserializer: ResponseDeserializer[ResponseType]

  def apply(reader: Reader, headersMap: HeadersMap): R
  def apply(reader: Reader): R = MessageReader.read(reader, apply)
  def from(s: String): R = MessageReader.from(s, apply)
}

trait RequestMetaCompanion[R <: RequestBase]
  extends RequestMeta[R] with RequestObservableMeta[R] {
  implicit val requestMeta: RequestMeta[R] = this
  implicit val observableMeta: RequestObservableMeta[R] = this
}

trait Response[+B <: Body] extends Message[B, ResponseHeaders]

// defines responses:
// * single:                DefinedResponse[Created[TestCreatedBody]]
// * multiple:              DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])]
trait DefinedResponse[R]

trait ResponseMeta[PB <: Body, R <: Response[PB]] {
  def statusCode: Int

  def apply[B <: PB](body: B, headersMap: HeadersMap)(implicit messagingContext: MessagingContext): R
  def apply[B <: PB](body: B)(implicit messagingContext: MessagingContext): R

  // def unapply[B <: PB](response: Response[PB]): Option[(B,Map[String,Seq[String]])] TODO: this doesn't works, find a workaround
}

// class UriMatchException(val uri: String, val uriPattern: UriPattern, cause: Throwable = null) extends RuntimeException(s"$uri doesn't match pattern $uriPattern", cause)