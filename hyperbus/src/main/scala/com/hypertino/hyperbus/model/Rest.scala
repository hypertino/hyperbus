package com.hypertino.hyperbus.model

import java.io.{Reader, StringWriter, Writer}

import com.hypertino.binders.core.BindOptions
import com.hypertino.hyperbus.serialization.{MessageReader, RequestDeserializer, ResponseDeserializer, SerializationOptions}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher

import scala.reflect.ClassTag

trait Writable {
  def serialize(writer: Writer)(implicit so: SerializationOptions)

  def serializeToString(implicit so: SerializationOptions): String = {
    val writer = new StringWriter()
    try {
      serialize(writer)(so)
      writer.toString
    }
    finally {
      writer.close()
    }
  }
}

//object Writable {
//  implicit val defaultBindOptions: BindOptions = BindOptions(skipOptionalFields = true)
//}

trait Body extends Writable {
  def contentType: Option[String]
}

trait BodyObjectApi[B <: Body] {
  def contentType: Option[String]
  def apply(reader: Reader, contentType: Option[String])
           (implicit so: com.hypertino.hyperbus.serialization.SerializationOptions): B
}

trait CollectionBody[T] extends Body {
  def items: Seq[T]
}

trait NoContentType {
  def contentType: Option[String] = None
}

trait DynamicBodyTrait

trait Message[+B <: Body, +H <: Headers] extends Writable {
  def headers: H

  def body: B

  def serialize(writer: Writer)(implicit so: SerializationOptions): Unit = {
    headers.serialize(writer)(so)
    writer.write("\r\n")
    body.serialize(writer)
  }

  override def toString: String = {
    s"${getClass.getName}[${body.getClass.getName}]:$serializeToString"
  }
}

trait Request[+B <: Body] extends Message[B, RequestHeaders] with MessagingContext {
  override def correlationId: String = headers.correlationId
}

trait RequestObservableMeta[R <: RequestBase] {
  def requestMatcher: RequestMatcher
}

trait RequestMeta[R <: RequestBase] {
  type ResponseType <: ResponseBase
  def responseDeserializer: ResponseDeserializer[ResponseType]

  def apply(reader: Reader, headersMap: HeadersMap): R
  def apply(reader: Reader): R = MessageReader.read(reader, apply)
  def from(s: String): R = MessageReader.fromString(s, apply)
}

trait RequestMetaCompanion[R <: RequestBase]
  extends RequestMeta[R] with RequestObservableMeta[R] {

  def location: String
  def method: String
  def contentType: Option[String]
  def requestMatcher: RequestMatcher
  def responseDeserializer: com.hypertino.hyperbus.serialization.ResponseDeserializer[ResponseType]
}

trait Response[+B <: Body] extends Message[B, ResponseHeaders]

// defines responses:
// * single:                DefinedResponse[Created[TestCreatedBody]]
// * multiple:              DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])]
trait DefinedResponse[R]

trait ResponseMeta[PB <: Body, R <: Response[PB]] {
  def statusCode: Int

  def apply[B <: PB](body: B, headersMap: HeadersMap)
                    (implicit messagingContext: MessagingContext): R
  def apply[B <: PB](body: B)
                    (implicit messagingContext: MessagingContext): R

  // def unapply[B <: PB](response: Response[PB]): Option[(B,Map[String,Seq[String]])] TODO: this doesn't works, find a workaround
}

// class UriMatchException(val uri: String, val uriPattern: UriPattern, cause: Throwable = null) extends RuntimeException(s"$uri doesn't match pattern $uriPattern", cause)