package com.hypertino.hyperbus.model

import java.io.{Reader, StringReader, StringWriter, Writer}

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.json.api.JsonParserApi
import com.hypertino.hyperbus.transport.api.uri.UriPattern

import scala.collection.mutable

case class Link(href: String, templated: Boolean = false, @fieldName("type") typ: Option[String] = None)

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

trait HalLinks {
  def links: Links
}

object Links {
  def apply(selfHref: String, templated: Boolean = false, typ: Option[String] = None): Links = {
    new LinksBuilder() self(selfHref, templated, typ) result()
  }

  def location(locationHref: String, templated: Boolean = false, typ: Option[String] = None): Links = {
    new LinksBuilder() location (locationHref, templated, typ) result()
  }

  def apply(key: String, link: Link): Links = new LinksBuilder() add(key, link) result()

  def apply(vargs: (String, Either[Link, Seq[Link]])*): Links = {
    new LinksBuilder() add vargs result()
  }
}

class LinksBuilder(private [this] val args: mutable.Map[String, Either[Link, Seq[Link]]]) {
  def this() = this(mutable.Map[String, Either[Link, Seq[Link]]]())

  def self(selfHref: String, templated: Boolean = true, typ: Option[String] = None) = {
    args += DefLink.SELF → Left(Link(selfHref, templated))
    this
  }
  def location(locationHref: String, templated: Boolean = true, typ: Option[String] = None) = {
    args += DefLink.LOCATION → Left(Link(locationHref, templated))
    this
  }
  def add(key: String, href: String, templated: Boolean = true, typ: Option[String] = None): LinksBuilder = {
    add(key, Link(href, templated, typ))
    this
  }
  def add(key: String, link : Link) = {
    args.get(key) match {
      case Some(Left(existingLink)) ⇒
        args += key → Right(Seq(existingLink, link))

      case Some(Right(existingLinks)) ⇒
        args += key → Right(existingLinks :+ link)

      case None ⇒
        args += key → Left(link)
    }
    this
  }
  def add(links: Seq[(String, Either[Link, Seq[Link]])]): LinksBuilder = {
    if (args.isEmpty) {
      args ++= links
    }
    else {
      links.foreach {
        case (k, Left(v)) ⇒ add(k, v)
        case (k, Right(v)) ⇒ v.foreach(vi ⇒ add(k, vi))
      }
    }
    this
  }
  def result(): Links = args.toMap
}

trait Message[+B <: Body, +H <: Headers] {
  def headers: H

  def body: B

  def serialize(writer: Writer): Unit = {
    headers.serialize(writer)
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

trait Request[+B <: Body] extends Message[B, RequestHeaders] {

  protected def assertHeaderValue(name: String, value: String): Unit = {
    val v = headers.stringHeader(name)
    if (v != value) {
      throw new IllegalArgumentException(s"Incorrect $name value: $v != $value (headers?)")
    }
  }
}

trait Response[+B <: Body] extends Message[B, ResponseHeaders] {
}

// defines responses:
// * single:                DefinedResponse[Created[TestCreatedBody]]
// * multiple:              DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])]
// * alternative multiple:  DefinedResponse[|[Ok[DynamicBody], |[Created[TestCreatedBody], !]]]
trait DefinedResponse[R]

trait |[L <: Response[Body], R <: Response[Body]] extends Response[Body]

trait ! extends Response[Body]

trait RequestObjectApi[R <: Request[Body]] {
  def uriPattern: UriPattern

  def method: String

  def apply(reader: Reader, headersMap: HeadersMap): R

  def apply(reader: Reader): R = {
    import com.hypertino.binders.json.JsonBinders._
    implicit val bindOptions = com.hypertino.hyperbus.serialization.bindOptions
    val headers = reader.readJson[HeadersMap]
    apply(reader, headers)
  }

  def apply(message: String): R = {
    val stringReader = new StringReader(message)
    try {
      apply(stringReader)
    }
    finally {
      stringReader.close()
    }
  }

  def withUriArgs(uri: String)(constructor: Map[String, String] ⇒ R): R = {
    uriPattern.matchUri(uri) match {
      case Some(map) ⇒ constructor(map)
      case None ⇒ throw new UriMatchException(uri, uriPattern)
    }
  }
}

trait ResponseObjectApi[PB <: Body, R <: Response[PB]] {
  def statusCode: Int
  def apply[B <: PB](body: B, headers: ResponseHeaders): R
  def apply[B <: PB](body: B, headersMap: HeadersMap)(implicit mcx: MessagingContext): R
  def apply[B <: PB](body: B)(implicit mcx: MessagingContext): R
  // def unapply[B <: PB](response: Response[PB]): Option[(B,Map[String,Seq[String]])] TODO: this doesn't works, find a workaround
}

class UriMatchException(val uri: String, val uriPattern: UriPattern, cause: Throwable = null) extends RuntimeException(s"$uri doesn't match pattern $uriPattern", cause)