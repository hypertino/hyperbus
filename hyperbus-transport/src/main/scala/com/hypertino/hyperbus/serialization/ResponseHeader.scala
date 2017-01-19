package com.hypertino.hyperbus.serialization

import com.hypertino.hyperbus.model.Headers
import com.hypertino.hyperbus.transport.api.EntityWithHeaders

case class ResponseHeader(status: Int, headers: Headers) extends EntityWithHeaders
