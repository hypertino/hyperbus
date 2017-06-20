package com.hypertino.hyperbus.model

import java.io.Writer

import com.hypertino.binders.value.{Null, Value}
import com.hypertino.hyperbus.serialization.JsonContentTypeConverter

import scala.collection.immutable.{ListMap, MapLike}

object HeadersMap {
  val empty: ListMap[String, Value] = ListMap.empty[String,Value]

  def apply(elements: (String, Value)*) = ListMap(elements: _*)
}

trait Headers extends Map[String, Value] {
  protected def underlying: HeadersMap

  def messageId: String = this.safe(Header.MESSAGE_ID).toString

  def correlationId: String = underlying.get(Header.CORRELATION_ID).map(_.toString).getOrElse(messageId)

  def contentType: Option[String] = underlying.get(Header.CONTENT_TYPE).map(_.toString)

  def serialize(writer: Writer) : Unit = {
    import com.hypertino.binders.json.JsonBinders._
    implicit val bindOptions = com.hypertino.hyperbus.serialization.bindOptions
    val transformedSeq = underlying.map {
      case (Header.CONTENT_TYPE, value) ⇒ Header.CONTENT_TYPE → JsonContentTypeConverter.localContentTypeToUniversalJson(value)
      case other ⇒ other
    }
    transformedSeq.writeJson(writer)
  }

  def safe(name: String): Value = {
    getOrElse(name, {
      val fullName = Header.fullNameMap.get(name).map(s ⇒ s" ($s)").getOrElse("")
      throw new NoSuchHeaderException(s"$name$fullName")
    })
  }
  def byPath(path: Seq[String]): Value = getOrElse(path.head, Null)(path.tail)

  /* map implementation */
  override def get(key: String): Option[Value] = underlying.get(key)

  override def iterator: Iterator[(String, Value)] = underlying.iterator
}

object Headers {
  def builder = new HeadersBuilder
}

case class RequestHeaders(underlying: HeadersMap) extends Headers with MapLike[String, Value, RequestHeaders] {
  def hrl: HRL = this.safe(Header.HRL).to[HRL]

  def method: String = this.safe(Header.METHOD).toString

  override def +[B1 >: Value](kv: (String, B1)): RequestHeaders = RequestHeaders((underlying + kv).asInstanceOf[HeadersMap])
  override def -(key: String): RequestHeaders = RequestHeaders(underlying - key)
  override def empty: RequestHeaders = RequestHeaders.empty
}

object RequestHeaders {
  val empty = RequestHeaders(HeadersMap.empty)
}

case class ResponseHeaders(underlying: HeadersMap) extends Headers with MapLike[String, Value, ResponseHeaders]{
  lazy val statusCode: Int = this.safe(Header.STATUS_CODE).toInt

  def location: HRL = this.safe(Header.LOCATION).to[HRL]

  override def +[B1 >: Value](kv: (String, B1)): ResponseHeaders = ResponseHeaders((underlying + kv).asInstanceOf[HeadersMap])
  override def -(key: String):ResponseHeaders = ResponseHeaders(underlying - key)
  override def empty: ResponseHeaders = ResponseHeaders.empty
}

object ResponseHeaders {
  val empty = ResponseHeaders(HeadersMap.empty)
}

