package com.hypertino.hyperbus

import com.hypertino.binders.value.Value

import scala.collection.generic.{GenMapFactory, MapFactory}

package object model {
  type HeadersMap = Map[String, Value]
  type Links = Map[String, Either[Link, Seq[Link]]]
  type RequestBase = Request[Body]
  type ResponseBase = Response[Body]

  implicit def requestToMessageContext(requestBase: RequestBase): MessagingContext = {
    MessagingContext(requestBase.headers.correlationId)
  }

  object HeadersMap {
    def empty = Map.empty[String, Value]
    def apply(elems: (String, Value)*): HeadersMap = (Map.newBuilder[String, Value] ++= elems).result()
  }

  implicit class HeadersMapWrapper(val h: HeadersMap) extends AnyVal {
    def safe(name: String): Value = h.getOrElse(name, throw new NoSuchHeaderException(name))
  }
}
