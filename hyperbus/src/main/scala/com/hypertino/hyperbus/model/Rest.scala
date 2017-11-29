/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

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

trait Body extends Writable with Serializable {
  def contentType: Option[String]
  override def toString: String = {
    implicit val so = SerializationOptions.forceOptionalFields
    s"${getClass.getName}:$serializeToString"
  }
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

//trait DynamicBodyTrait

trait Message[+B <: Body, +H <: MessageHeaders] extends Writable with Serializable {
  def headers: H

  def body: B

  def copyWithHeaders(headers: Headers): Message[B, H]

  def serialize(writer: Writer)(implicit so: SerializationOptions): Unit = {
    headers.serialize(writer)(so)
    writer.write("\r\n")
    body.serialize(writer)
  }

  override def toString: String = {
    implicit val so = SerializationOptions.forceOptionalFields
    s"${getClass.getName}[${body.getClass.getName}]:$serializeToString"
  }
}

trait Request[+B <: Body] extends Message[B, RequestHeaders] with MessagingContext {
  override def correlationId: String = headers.correlationId
  override def parentId: Option[String] = Some(headers.messageId)
}

trait RequestObservableMeta[R <: RequestBase] {
  def requestMatcher: RequestMatcher
}

trait RequestMeta[R <: RequestBase] {
  type ResponseType <: ResponseBase
  def responseDeserializer: ResponseDeserializer[ResponseType]

  def apply(reader: Reader, headers: Headers): R
  def apply(reader: Reader): R = MessageReader.read(reader, apply)
  def from(s: String): R = MessageReader.fromString(s, apply)
}

trait RequestMetaCompanion[R <: RequestBase]
  extends RequestMeta[R] with RequestObservableMeta[R] {

  def location: String
  def method: String
  def contentType: Option[String]
}

trait Response[+B <: Body] extends Message[B, ResponseHeaders]

// defines responses:
// * single:                DefinedResponse[Created[TestCreatedBody]]
// * multiple:              DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])]
trait DefinedResponse[R]

trait ResponseMeta[PB <: Body, R <: Response[PB]] {
  def statusCode: Int

  def apply[B <: PB](body: B, headers: Headers)
                    (implicit messagingContext: MessagingContext): R
  def apply[B <: PB](body: B)
                    (implicit messagingContext: MessagingContext): R

  // def unapply[B <: PB](response: Response[PB]): Option[(B,Map[String,Seq[String]])] TODO: this doesn't works, find a workaround
}

trait ErrorResponseMeta[PB <: Body, R <: Response[PB]] extends ResponseMeta[PB, R] {
  def apply()(implicit messagingContext: MessagingContext): R
}
