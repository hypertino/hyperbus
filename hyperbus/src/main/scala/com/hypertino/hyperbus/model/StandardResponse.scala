/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model

import java.io.Reader

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.serialization._

object StandardResponse {

  def apply(reader: Reader,
            headers: Headers,
            bodyDeserializer: PartialFunction[ResponseHeaders, ResponseBodyDeserializer],
            throwIfError: Boolean
           ): ResponseBase = {
    val responseHeaders = ResponseHeaders(headers)
    val body =
      if (bodyDeserializer.isDefinedAt(responseHeaders))
        bodyDeserializer(responseHeaders)(reader, responseHeaders.contentType)
      else {
        if (isError(responseHeaders)) {
          ErrorBody(reader, responseHeaders.contentType)
        }
        else {
          DynamicBody(reader, responseHeaders.contentType)
        }
      }

    apply(body, responseHeaders) match {
      case e: HyperbusError[ErrorBody] @unchecked if throwIfError ⇒ throw e
      case other ⇒ other
    }
  }

  def apply(reader: Reader,
            headers: Headers): DynamicResponse = {
    apply(reader, headers, PartialFunction.empty, throwIfError = false).asInstanceOf[DynamicResponse]
  }

  def dynamicDeserializer: ResponseDeserializer[DynamicResponse] = apply

  def isError(responseHeaders: ResponseHeaders): Boolean = responseHeaders.statusCode >= 400 && responseHeaders.statusCode <= 599

  def apply(body: Body, headers: ResponseHeaders): ResponseBase = {
    headers.statusCode match {
      case Status.OK => Ok(body, headers)
      case Status.CREATED => Created(body, headers)
      case Status.ACCEPTED => Accepted(body, headers)
      case Status.NON_AUTHORITATIVE_INFORMATION => NonAuthoritativeInformation(body, headers)
      case Status.NO_CONTENT => NoContent(body, headers)
      case Status.RESET_CONTENT => ResetContent(body, headers)
      case Status.PARTIAL_CONTENT => PartialContent(body, headers)
      case Status.MULTI_STATUS => MultiStatus(body, headers)

      case Status.MULTIPLE_CHOICES => MultipleChoices(body, headers)
      case Status.MOVED_PERMANENTLY => MovedPermanently(body, headers)
      case Status.FOUND => Found(body, headers)
      case Status.SEE_OTHER => SeeOther(body, headers)
      case Status.NOT_MODIFIED => NotModified(body, headers)
      case Status.USE_PROXY => UseProxy(body, headers)
      case Status.TEMPORARY_REDIRECT => TemporaryRedirect(body, headers)

      case Status.BAD_REQUEST => BadRequest(body, headers)
      case Status.UNAUTHORIZED => Unauthorized(body, headers)
      case Status.PAYMENT_REQUIRED => PaymentRequired(body, headers)
      case Status.FORBIDDEN => Forbidden(body, headers)
      case Status.NOT_FOUND => NotFound(body, headers)
      case Status.METHOD_NOT_ALLOWED => MethodNotAllowed(body, headers)
      case Status.NOT_ACCEPTABLE => NotAcceptable(body, headers)
      case Status.PROXY_AUTHENTICATION_REQUIRED => ProxyAuthenticationRequired(body, headers)
      case Status.REQUEST_TIMEOUT => RequestTimeout(body, headers)
      case Status.CONFLICT => Conflict(body, headers)
      case Status.GONE => Gone(body, headers)
      case Status.LENGTH_REQUIRED => LengthRequired(body, headers)
      case Status.PRECONDITION_FAILED => PreconditionFailed(body, headers)
      case Status.REQUEST_ENTITY_TOO_LARGE => RequestEntityTooLarge(body, headers)
      case Status.REQUEST_URI_TOO_LONG => RequestUriTooLong(body, headers)
      case Status.UNSUPPORTED_MEDIA_TYPE => UnsupportedMediaType(body, headers)
      case Status.REQUESTED_RANGE_NOT_SATISFIABLE => RequestedRangeNotSatisfiable(body, headers)
      case Status.EXPECTATION_FAILED => ExpectationFailed(body, headers)
      case Status.UNPROCESSABLE_ENTITY => UnprocessableEntity(body, headers)
      case Status.LOCKED => Locked(body, headers)
      case Status.FAILED_DEPENDENCY => FailedDependency(body, headers)
      case Status.TOO_MANY_REQUESTS => TooManyRequests(body, headers)

      case Status.INTERNAL_SERVER_ERROR => InternalServerError(body, headers)
      case Status.NOT_IMPLEMENTED => NotImplemented(body, headers)
      case Status.BAD_GATEWAY => BadGateway(body, headers)
      case Status.SERVICE_UNAVAILABLE => ServiceUnavailable(body, headers)
      case Status.GATEWAY_TIMEOUT => GatewayTimeout(body, headers)
      case Status.HTTP_VERSION_NOT_SUPPORTED => HttpVersionNotSupported(body, headers)
      case Status.INSUFFICIENT_STORAGE => InsufficientStorage(body, headers)
    }
  }
}
