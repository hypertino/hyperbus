package com.hypertino.hyperbus.serialization

import com.hypertino.hyperbus.transport.api.{EntityWithHeaders, Header, Headers}

case class ResponseHeader(status: Int, headers: Headers) extends EntityWithHeaders
