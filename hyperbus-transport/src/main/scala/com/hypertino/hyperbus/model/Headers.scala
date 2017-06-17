package com.hypertino.hyperbus.model

import java.io.Writer

import com.hypertino.binders.value.{Null, Value}

import scala.collection.immutable.{ListMap, MapLike}

object HeadersMap {
  val empty: ListMap[String, Value] = ListMap.empty[String,Value]
  def builder = new HeadersBuilder

  def apply(elements: (String, Value)*) = ListMap(elements: _*)
}

trait Headers extends Map[String, Value] {
  protected def all: HeadersMap

  def messageId: String = this.safe(Header.MESSAGE_ID).toString

  def correlationId: Option[String] = all.get(Header.CORRELATION_ID).map(_.toString).orElse(Some(messageId))

  def contentType: Option[String] = all.get(Header.CONTENT_TYPE).map(_.toString)

  def serialize(writer: Writer) : Unit = {
    import com.hypertino.binders.json.JsonBinders._
    implicit val bindOptions = com.hypertino.hyperbus.serialization.bindOptions
    all.writeJson(writer)
  }

  def safe(name: String): Value = getOrElse(name, throw new NoSuchHeaderException(name))
  def byPath(path: Seq[String]): Value = getOrElse(path.head, Null)(path.tail)

  /* map implementation */
  override def get(key: String): Option[Value] = all.get(key)

  override def iterator: Iterator[(String, Value)] = all.iterator
}

case class RequestHeaders(all: HeadersMap) extends Headers with MapLike[String, Value, RequestHeaders] {
  def hrl: HRL = this.safe(Header.HRL).to[HRL]

  def method: String = this.safe(Header.METHOD).toString

  override def +[B1 >: Value](kv: (String, B1)): RequestHeaders = RequestHeaders((all + kv).asInstanceOf[HeadersMap])
  override def -(key: String): RequestHeaders = RequestHeaders(all - key)
  override def empty: RequestHeaders = RequestHeaders.empty
}

object RequestHeaders {
  val empty = RequestHeaders(HeadersMap.empty)
}

case class ResponseHeaders(all: HeadersMap) extends Headers with MapLike[String, Value, ResponseHeaders]{
  lazy val statusCode: Int = all(Header.STATUS_CODE).toInt

  def location: HRL = all(Header.LOCATION).to[HRL]

  override def +[B1 >: Value](kv: (String, B1)): ResponseHeaders = ResponseHeaders((all + kv).asInstanceOf[HeadersMap])
  override def -(key: String):ResponseHeaders = ResponseHeaders(all - key)
  override def empty: ResponseHeaders = ResponseHeaders.empty
}

object ResponseHeaders {
  val empty = ResponseHeaders(HeadersMap.empty)
}

