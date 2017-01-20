package com.hypertino.hyperbus.serialization

import com.hypertino.hyperbus.model.{EntityWithHeaders, Headers}

case class ResponseHeader(status: Int, headers: Headers) extends EntityWithHeaders
