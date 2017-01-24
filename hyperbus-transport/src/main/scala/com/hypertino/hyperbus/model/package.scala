package com.hypertino.hyperbus

import com.hypertino.binders.value.Value

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
  }
}
