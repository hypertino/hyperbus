/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model

import java.io.Writer

import com.hypertino.binders.value.{Null, Value}
import com.hypertino.hyperbus.serialization.{JsonContentTypeConverter, SerializationOptions}

import scala.collection.immutable.{ListMap, MapLike}

object Headers {
  val empty: ListMap[String, Value] = ListMap.empty[String,Value]
  def apply(elements: (String, Value)*) = ListMap(elements.map(kv => kv._1.toLowerCase() → kv._2): _*)
}

trait MessageHeaders extends Map[String, Value] {
  protected def underlying: Headers

  def messageId: String = this(Header.MESSAGE_ID).toString

  def correlationId: String = underlying.get(Header.CORRELATION_ID).map(_.toString).getOrElse(messageId)

  def contentType: Option[String] = underlying.get(Header.CONTENT_TYPE).map(_.toString)

  def link: Map[String, HRL] = underlying.get(Header.LINK).map(_.to[Map[String, HRL]]).getOrElse(Map.empty)

  def serialize(writer: Writer)(implicit so: SerializationOptions) : Unit = {
    import com.hypertino.binders.json.JsonBinders._
    import so._
    val transformedSeq = underlying.map {
      case (Header.CONTENT_TYPE, value) ⇒ Header.CONTENT_TYPE → JsonContentTypeConverter.localContentTypeToUniversalJson(value)
      case other ⇒ other
    }
    transformedSeq.writeJson(writer)
  }

  def byPath(path: Seq[String]): Value = getOrElse(path.head, Null)(path.tail)

  /* map implementation */
  override def get(key: String): Option[Value] = underlying.get(key.toLowerCase)
  override def iterator: Iterator[(String, Value)] = underlying.iterator
  override def apply(key: String): Value = {
    getOrElse(key, {
      val fullName = Header.fullNameMap.get(key).map(s ⇒ s" ($s)").getOrElse("")
      throw new NoSuchHeaderException(s"$key$fullName")
    })
  }
}

object MessageHeaders {
  def builder = new HeadersBuilder
}

case class RequestHeaders(underlying: Headers) extends MessageHeaders with MapLike[String, Value, RequestHeaders] {
  def hrl: HRL = this(Header.HRL).to[HRL]

  def method: String = this(Header.METHOD).toString

  override def +[B1 >: Value](kv: (String, B1)): RequestHeaders = RequestHeaders((underlying + kv).asInstanceOf[Headers])
  override def -(key: String): RequestHeaders = RequestHeaders(underlying - key)
  override def empty: RequestHeaders = RequestHeaders.empty
}

object RequestHeaders {
  val empty = RequestHeaders(Headers.empty)
}

case class ResponseHeaders(underlying: Headers) extends MessageHeaders with MapLike[String, Value, ResponseHeaders]{
  lazy val statusCode: Int = this(Header.STATUS_CODE).toInt

  def location: HRL = this(Header.LOCATION).to[HRL]

  override def +[B1 >: Value](kv: (String, B1)): ResponseHeaders = ResponseHeaders((underlying + kv).asInstanceOf[Headers])
  override def -(key: String):ResponseHeaders = ResponseHeaders(underlying - key)
  override def empty: ResponseHeaders = ResponseHeaders.empty
}

object ResponseHeaders {
  val empty = ResponseHeaders(Headers.empty)
}

