/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model

import com.hypertino.hyperbus.model.annotations.response
import com.hypertino.hyperbus.serialization.SerializationOptions

trait NormalResponse extends ResponseBase

trait RedirectResponse extends ResponseBase

trait ErrorResponse extends Response[Body]

trait ServerError extends ErrorResponse

trait ClientError extends ErrorResponse

trait ResponseMetaWithLocation[PB <: Body, R <: Response[PB]] extends ResponseMeta[PB, R] {
  import com.hypertino.binders.value._

  def apply[B <: PB](body: B, location: HRL, headers: Headers)
                    (implicit mcx: MessagingContext, so: SerializationOptions): R = {
    implicit val bindOptions = so.bindOptions
    apply[B](body, headers ++ Seq(Header.LOCATION → location.toValue))(mcx)
  }

  def apply[B <: PB](body: B, location: HRL)
                    (implicit mcx: MessagingContext, so: SerializationOptions): R = {
    implicit val bindOptions = so.bindOptions
    apply[B](body, Headers(Header.LOCATION → location.toValue))(mcx)
  }
}

// ----------------- Normal responses -----------------

import Status._

@response(Status.OK) case class Ok[+B <: Body](body: B) extends NormalResponse with Response[B]

object Ok extends ResponseMetaWithLocation[Body, Ok[Body]]

@response(Status.CREATED) case class Created[+B <: Body](body: B) extends NormalResponse with Response[B]

object Created extends ResponseMetaWithLocation[Body, Created[Body]]

@response(Status.ACCEPTED) case class Accepted[+B <: Body](body: B) extends NormalResponse with Response[B]

object Accepted extends ResponseMeta[Body, Accepted[Body]]

@response(Status.NON_AUTHORITATIVE_INFORMATION) case class NonAuthoritativeInformation[+B <: Body](body: B) extends NormalResponse with Response[B]

object NonAuthoritativeInformation extends ResponseMeta[Body, NonAuthoritativeInformation[Body]]

@response(Status.NO_CONTENT) case class NoContent[+B <: Body](body: B) extends NormalResponse with Response[B]

object NoContent extends ResponseMeta[Body, NoContent[Body]]

@response(Status.RESET_CONTENT) case class ResetContent[+B <: Body](body: B) extends NormalResponse with Response[B]

object ResetContent extends ResponseMeta[Body, ResetContent[Body]]

@response(Status.PARTIAL_CONTENT) case class PartialContent[+B <: Body](body: B) extends NormalResponse with Response[B]

object PartialContent extends ResponseMeta[Body, PartialContent[Body]]

@response(Status.MULTI_STATUS) case class MultiStatus[+B <: Body](body: B) extends NormalResponse with Response[B]

object MultiStatus extends ResponseMeta[Body, MultiStatus[Body]]

// ----------------- Redirect responses -----------------

// todo: URL for redirects like for created?

@response(Status.MULTIPLE_CHOICES) case class MultipleChoices[+B <: Body](body: B) extends RedirectResponse with Response[B]

object MultipleChoices extends ResponseMetaWithLocation[Body, MultipleChoices[Body]]

@response(Status.MOVED_PERMANENTLY) case class MovedPermanently[+B <: Body](body: B) extends RedirectResponse with Response[B]

object MovedPermanently extends ResponseMetaWithLocation[Body, MovedPermanently[Body]]

@response(Status.FOUND) case class Found[+B <: Body](body: B) extends RedirectResponse with Response[B]

object Found extends ResponseMetaWithLocation[Body, Found[Body]]

@response(Status.SEE_OTHER) case class SeeOther[+B <: Body](body: B) extends RedirectResponse with Response[B]

object SeeOther extends ResponseMetaWithLocation[Body, SeeOther[Body]]

@response(Status.NOT_MODIFIED) case class NotModified[+B <: Body](body: B) extends RedirectResponse with Response[B]

object NotModified extends ResponseMetaWithLocation[Body, NotModified[Body]]

@response(Status.USE_PROXY) case class UseProxy[+B <: Body](body: B) extends RedirectResponse with Response[B]

object UseProxy extends ResponseMetaWithLocation[Body, UseProxy[Body]]

@response(Status.TEMPORARY_REDIRECT) case class TemporaryRedirect[+B <: Body](body: B) extends RedirectResponse with Response[B]

object TemporaryRedirect extends ResponseMetaWithLocation[Body, TemporaryRedirect[Body]]

// ----------------- Exception base classes -----------------

abstract class HyperbusError[+B <: Body](body: B)
  extends RuntimeException with Response[B] {
  override def getMessage: String = {
    s"${body.serializeToString} (i#${headers.messageId} p#${headers.parentId} c#${headers.correlationId})"
  }
}

abstract class HyperbusServerError[+B <: Body](body: B) extends HyperbusError(body)

abstract class HyperbusClientError[+B <: Body](body: B) extends HyperbusError(body)

// ----------------- Client Error responses -----------------

@response(BAD_REQUEST) case class BadRequest[+B <: Body](body: B) extends HyperbusClientError(body)

object BadRequest extends ErrorResponseMeta[Body, BadRequest[Body]]

@response(Status.UNAUTHORIZED) case class Unauthorized[+B <: Body](body: B) extends HyperbusClientError(body)

object Unauthorized extends ErrorResponseMeta[Body, Unauthorized[Body]]

@response(Status.PAYMENT_REQUIRED) case class PaymentRequired[+B <: Body](body: B) extends HyperbusClientError(body)

object PaymentRequired extends ErrorResponseMeta[Body, PaymentRequired[Body]]

@response(Status.FORBIDDEN) case class Forbidden[+B <: Body](body: B) extends HyperbusClientError(body)

object Forbidden extends ErrorResponseMeta[Body, Forbidden[Body]]

@response(Status.NOT_FOUND) case class NotFound[+B <: Body](body: B) extends HyperbusClientError(body)

object NotFound extends ErrorResponseMeta[Body, NotFound[Body]]

@response(Status.METHOD_NOT_ALLOWED) case class MethodNotAllowed[+B <: Body](body: B) extends HyperbusClientError(body)

object MethodNotAllowed extends ErrorResponseMeta[Body, MethodNotAllowed[Body]]

@response(Status.NOT_ACCEPTABLE) case class NotAcceptable[+B <: Body](body: B) extends HyperbusClientError(body)

object NotAcceptable extends ErrorResponseMeta[Body, NotAcceptable[Body]]

@response(Status.PROXY_AUTHENTICATION_REQUIRED) case class ProxyAuthenticationRequired[+B <: Body](body: B) extends HyperbusClientError(body)

object ProxyAuthenticationRequired extends ErrorResponseMeta[Body, ProxyAuthenticationRequired[Body]]

@response(Status.REQUEST_TIMEOUT) case class RequestTimeout[+B <: Body](body: B) extends HyperbusClientError(body)

object RequestTimeout extends ErrorResponseMeta[Body, RequestTimeout[Body]]

@response(Status.CONFLICT) case class Conflict[+B <: Body](body: B) extends HyperbusClientError(body)

object Conflict extends ErrorResponseMeta[Body, Conflict[Body]]

@response(Status.GONE) case class Gone[+B <: Body](body: B) extends HyperbusClientError(body)

object Gone extends ErrorResponseMeta[Body, Gone[Body]]

@response(Status.LENGTH_REQUIRED) case class LengthRequired[+B <: Body](body: B) extends HyperbusClientError(body)

object LengthRequired extends ErrorResponseMeta[Body, LengthRequired[Body]]

@response(Status.PRECONDITION_FAILED) case class PreconditionFailed[+B <: Body](body: B) extends HyperbusClientError(body)

object PreconditionFailed extends ErrorResponseMeta[Body, PreconditionFailed[Body]]

@response(Status.REQUEST_ENTITY_TOO_LARGE) case class RequestEntityTooLarge[+B <: Body](body: B) extends HyperbusClientError(body)

object RequestEntityTooLarge extends ErrorResponseMeta[Body, RequestEntityTooLarge[Body]]

@response(Status.REQUEST_URI_TOO_LONG) case class RequestUriTooLong[+B <: Body](body: B) extends HyperbusClientError(body)

object RequestUriTooLong extends ErrorResponseMeta[Body, RequestUriTooLong[Body]]

@response(Status.UNSUPPORTED_MEDIA_TYPE) case class UnsupportedMediaType[+B <: Body](body: B) extends HyperbusClientError(body)

object UnsupportedMediaType extends ErrorResponseMeta[Body, UnsupportedMediaType[Body]]

@response(Status.REQUESTED_RANGE_NOT_SATISFIABLE) case class RequestedRangeNotSatisfiable[+B <: Body](body: B) extends HyperbusClientError(body)

object RequestedRangeNotSatisfiable extends ErrorResponseMeta[Body, RequestedRangeNotSatisfiable[Body]]

@response(Status.EXPECTATION_FAILED) case class ExpectationFailed[+B <: Body](body: B) extends HyperbusClientError(body)

object ExpectationFailed extends ErrorResponseMeta[Body, ExpectationFailed[Body]]

@response(Status.UNPROCESSABLE_ENTITY) case class UnprocessableEntity[+B <: Body](body: B) extends HyperbusClientError(body)

object UnprocessableEntity extends ErrorResponseMeta[Body, UnprocessableEntity[Body]]

@response(Status.LOCKED) case class Locked[+B <: Body](body: B) extends HyperbusClientError(body)

object Locked extends ErrorResponseMeta[Body, Locked[Body]]

@response(Status.FAILED_DEPENDENCY) case class FailedDependency[+B <: Body](body: B) extends HyperbusClientError(body)

object FailedDependency extends ErrorResponseMeta[Body, FailedDependency[Body]]

@response(Status.TOO_MANY_REQUESTS) case class TooManyRequests[+B <: Body](body: B) extends HyperbusClientError(body)

object TooManyRequests extends ErrorResponseMeta[Body, TooManyRequests[Body]]

// ----------------- Server Error responses -----------------

@response(Status.INTERNAL_SERVER_ERROR) case class InternalServerError[+B <: Body](body: B) extends HyperbusServerError(body)

object InternalServerError extends ErrorResponseMeta[Body, InternalServerError[Body]]

@response(Status.NOT_IMPLEMENTED) case class NotImplemented[+B <: Body](body: B) extends HyperbusServerError(body)

object NotImplemented extends ErrorResponseMeta[Body, NotImplemented[Body]]

@response(Status.BAD_GATEWAY) case class BadGateway[+B <: Body](body: B) extends HyperbusServerError(body)

object BadGateway extends ErrorResponseMeta[Body, BadGateway[Body]]

@response(Status.SERVICE_UNAVAILABLE) case class ServiceUnavailable[+B <: Body](body: B) extends HyperbusServerError(body)

object ServiceUnavailable extends ErrorResponseMeta[Body, ServiceUnavailable[Body]]

@response(Status.GATEWAY_TIMEOUT) case class GatewayTimeout[+B <: Body](body: B) extends HyperbusServerError(body)

object GatewayTimeout extends ErrorResponseMeta[Body, GatewayTimeout[Body]]

@response(Status.HTTP_VERSION_NOT_SUPPORTED) case class HttpVersionNotSupported[+B <: Body](body: B) extends HyperbusServerError(body)

object HttpVersionNotSupported extends ErrorResponseMeta[Body, HttpVersionNotSupported[Body]]

@response(Status.INSUFFICIENT_STORAGE) case class InsufficientStorage[+B <: Body](body: B) extends HyperbusServerError(body)

object InsufficientStorage extends ErrorResponseMeta[Body, InsufficientStorage[Body]]
