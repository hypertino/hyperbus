package com.hypertino.hyperbus.model

import java.io.{Reader, StringReader, StringWriter, Writer}

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.serialization.MessageReader

import scala.collection.mutable

trait Body {
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

trait Message[+B <: Body, +H <: Headers] {
  def headers: H

  def body: B

  def serialize(writer: Writer): Unit = {
    headers.serialize(writer)
    writer.write("\r\n")
    body.serialize(writer)
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

  override def toString = {
    s"${getClass.getName}[${body.getClass.getName}]:$serializeToString"
  }
}

trait Request[+B <: Body] extends Message[B, RequestHeaders] with MessagingContext {
  override def correlationId: Option[String] = headers.correlationId
//  protected def assertHeaderValue(name: String, value: String): Unit = {
//    val v = headers.stringHeader(name)
//    if (v != value) {
//      throw new IllegalArgumentException(s"Incorrect $name value: $v != $value (headers?)")
//    }
//  }
}

trait Response[+B <: Body] extends Message[B, ResponseHeaders]

// defines responses:
// * single:                DefinedResponse[Created[TestCreatedBody]]
// * multiple:              DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])]
// * alternative multiple:  DefinedResponse[|[Ok[DynamicBody], |[Created[TestCreatedBody], !]]]
trait DefinedResponse[R]

trait |[L <: Response[Body], R <: Response[Body]] extends Response[Body]

trait ! extends Response[Body]

trait RequestObjectApi[R <: Request[Body]] {
  def serviceAddress: String
  def method: String

  def apply(reader: Reader, headersObj: Obj): R
  def apply(reader: Reader): R = MessageReader(reader, apply(_, _))
  def apply(message: String): R = MessageReader(message, apply(_, _))
}

trait ResponseObjectApi[PB <: Body, R <: Response[PB]] {
  def statusCode: Int

//  def apply[B <: PB](reader: Reader, headersObj: Obj): R
//  def apply[B <: PB](reader: Reader): R = MessageReader(reader, apply[B](_, _))
//  def apply[B <: PB](message: String): R = MessageReader(message, apply[B](_, _))
//
//  def apply[B <: PB](body: B, headers: ResponseHeaders): R
//  def apply[B <: PB](body: B, headersObj: Obj)(implicit mcx: MessagingContext): R
//  def apply[B <: PB](body: B)(implicit mcx: MessagingContext): R
  // def unapply[B <: PB](response: Response[PB]): Option[(B,Map[String,Seq[String]])] TODO: this doesn't works, find a workaround
}

// class UriMatchException(val uri: String, val uriPattern: UriPattern, cause: Throwable = null) extends RuntimeException(s"$uri doesn't match pattern $uriPattern", cause)