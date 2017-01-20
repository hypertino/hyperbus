package com.hypertino.hyperbus.model

import java.io.{StringWriter, Writer}

import com.hypertino.binders.annotations.fieldName
import com.hypertino.hyperbus.serialization.MessageSerializer
import com.hypertino.hyperbus.transport.api.uri.Uri

import scala.collection.mutable

case class Link(href: String, templated: Boolean = false, @fieldName("type") typ: Option[String] = None)

trait Body {
  def contentType: Option[String]

  def serialize(writer: Writer)
}

trait BodyObjectApi[B <: Body] {
  def contentType: Option[String]
  def apply(contentType: Option[String], jsonParser : com.fasterxml.jackson.core.JsonParser): B
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

trait Message[+B <: Body] extends MessagingContext with EntityWithHeaders {
  def messageId: String

  def body: B

  def serialize(writer: Writer)

  def serializeToString: String = {
    val writer = new StringWriter()
    serialize(writer)
    writer.toString
  }

  override def toString = {
    s"${getClass.getName}[${body.getClass.getName}]:$serializeToString"
  }
}

trait Request[+B <: Body] extends Message[B] {
  def uri: Uri

  def method: String = header(Header.METHOD).toString

  protected def assertMethod(value: String): Unit = {
    if (method != value) throw new IllegalArgumentException(s"Incorrect method value: $method != $value (headers?)")
  }

  override def serialize(writer: Writer) = MessageSerializer.serializeRequest(this, writer)
}

trait RequestObjectApi[R <: Request[Body]] {
  def apply(requestHeader: com.hypertino.hyperbus.serialization.RequestHeader, jsonParser : com.fasterxml.jackson.core.JsonParser): R
  def uriPattern: String
  def method: String
}

trait Response[+B <: Body] extends Message[B] {
  def statusCode: Int

  override def serialize(writer: Writer) = MessageSerializer.serializeResponse(this, writer)
}

// defines responses:
// * single:                DefinedResponse[Created[TestCreatedBody]]
// * multiple:              DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])]
// * alternative multiple:  DefinedResponse[|[Ok[DynamicBody], |[Created[TestCreatedBody], !]]]
trait DefinedResponse[R]

trait |[L <: Response[Body], R <: Response[Body]] extends Response[Body]

trait ! extends Response[Body]
