package com.hypertino.hyperbus.model

import java.io.Reader


object StandardResponseBody {
  def apply(reader: Reader, responseHeaders: ResponseHeaders): Body = {
    if (responseHeaders.statusCode >= 400 && responseHeaders.statusCode <= 599)
      ErrorBody(reader, responseHeaders.contentType)
    else {
      responseHeaders.statusCode match {
        case Status.CREATED ⇒
          DynamicCreatedBody(reader, responseHeaders.contentType)

        case _ ⇒
          DynamicBody(reader, responseHeaders.contentType)
      }
    }
  }
}
