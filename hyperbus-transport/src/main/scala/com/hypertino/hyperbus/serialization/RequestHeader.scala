package com.hypertino.hyperbus.serialization

import com.hypertino.hyperbus.model.{Header, Headers}
import com.hypertino.hyperbus.transport.api.EntityWithHeaders
import com.hypertino.hyperbus.transport.api.uri.Uri

case class RequestHeader(uri: Uri, headers: Headers) extends EntityWithHeaders {
  def method: String = header(Header.METHOD).toString
}
