package com.hypertino.hyperbus.model

import com.hypertino.hyperbus.model.annotations.response

trait NormalResponse extends ResponseBase

trait RedirectResponse extends ResponseBase

trait ErrorResponse extends Response[ErrorBody]

trait ServerError extends ErrorResponse

trait ClientError extends ErrorResponse

trait ResponseMetaWithLocation[PB <: Body, R <: Response[PB]] extends ResponseMeta[PB, R] {
  import com.hypertino.binders.value._

  def apply[B <: PB](body: B, location: HRI, headersObj: Obj)(implicit mcx: MessagingContext): R = {
    apply[B](body, headersObj + Seq(Header.LOCATION → location.toValue))(mcx)
  }

  def apply[B <: PB](body: B, location: HRI)(implicit mcx: MessagingContext): R = {
    apply[B](body, Obj.from(Header.LOCATION → location.toValue))(mcx)
  }
}

// ----------------- Normal responses -----------------

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

abstract class HyperbusException[+B <: ErrorBody](body: B)
  extends RuntimeException(body.toString) with Response[B] {
}

abstract class HyperbusServerException[+B <: ErrorBody](body: B) extends HyperbusException(body)

abstract class HyperbusClientException[+B <: ErrorBody](body: B) extends HyperbusException(body)

// ----------------- Client Error responses -----------------

@response(Status.BAD_REQUEST) case class BadRequest[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object BadRequest extends ResponseMeta[ErrorBody, BadRequest[ErrorBody]]

@response(Status.UNAUTHORIZED) case class Unauthorized[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object Unauthorized extends ResponseMeta[ErrorBody, Unauthorized[ErrorBody]]

@response(Status.PAYMENT_REQUIRED) case class PaymentRequired[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object PaymentRequired extends ResponseMeta[ErrorBody, PaymentRequired[ErrorBody]]

@response(Status.FORBIDDEN) case class Forbidden[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object Forbidden extends ResponseMeta[ErrorBody, Forbidden[ErrorBody]]

@response(Status.NOT_FOUND) case class NotFound[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object NotFound extends ResponseMeta[ErrorBody, NotFound[ErrorBody]]

@response(Status.METHOD_NOT_ALLOWED) case class MethodNotAllowed[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object MethodNotAllowed extends ResponseMeta[ErrorBody, MethodNotAllowed[ErrorBody]]

@response(Status.NOT_ACCEPTABLE) case class NotAcceptable[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object NotAcceptable extends ResponseMeta[ErrorBody, NotAcceptable[ErrorBody]]

@response(Status.PROXY_AUTHENTICATION_REQUIRED) case class ProxyAuthenticationRequired[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object ProxyAuthenticationRequired extends ResponseMeta[ErrorBody, ProxyAuthenticationRequired[ErrorBody]]

@response(Status.REQUEST_TIMEOUT) case class RequestTimeout[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object RequestTimeout extends ResponseMeta[ErrorBody, RequestTimeout[ErrorBody]]

@response(Status.CONFLICT) case class Conflict[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object Conflict extends ResponseMeta[ErrorBody, Conflict[ErrorBody]]

@response(Status.GONE) case class Gone[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object Gone extends ResponseMeta[ErrorBody, Gone[ErrorBody]]

@response(Status.LENGTH_REQUIRED) case class LengthRequired[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object LengthRequired extends ResponseMeta[ErrorBody, LengthRequired[ErrorBody]]

@response(Status.PRECONDITION_FAILED) case class PreconditionFailed[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object PreconditionFailed extends ResponseMeta[ErrorBody, PreconditionFailed[ErrorBody]]

@response(Status.REQUEST_ENTITY_TOO_LARGE) case class RequestEntityTooLarge[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object RequestEntityTooLarge extends ResponseMeta[ErrorBody, RequestEntityTooLarge[ErrorBody]]

@response(Status.REQUEST_URI_TOO_LONG) case class RequestUriTooLong[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object RequestUriTooLong extends ResponseMeta[ErrorBody, RequestUriTooLong[ErrorBody]]

@response(Status.UNSUPPORTED_MEDIA_TYPE) case class UnsupportedMediaType[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object UnsupportedMediaType extends ResponseMeta[ErrorBody, UnsupportedMediaType[ErrorBody]]

@response(Status.REQUESTED_RANGE_NOT_SATISFIABLE) case class RequestedRangeNotSatisfiable[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object RequestedRangeNotSatisfiable extends ResponseMeta[ErrorBody, RequestedRangeNotSatisfiable[ErrorBody]]

@response(Status.EXPECTATION_FAILED) case class ExpectationFailed[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object ExpectationFailed extends ResponseMeta[ErrorBody, ExpectationFailed[ErrorBody]]

@response(Status.UNPROCESSABLE_ENTITY) case class UnprocessableEntity[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object UnprocessableEntity extends ResponseMeta[ErrorBody, UnprocessableEntity[ErrorBody]]

@response(Status.LOCKED) case class Locked[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object Locked extends ResponseMeta[ErrorBody, Locked[ErrorBody]]

@response(Status.FAILED_DEPENDENCY) case class FailedDependency[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object FailedDependency extends ResponseMeta[ErrorBody, FailedDependency[ErrorBody]]

@response(Status.TOO_MANY_REQUEST) case class TooManyRequest[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object TooManyRequest extends ResponseMeta[ErrorBody, TooManyRequest[ErrorBody]]

// ----------------- Server Error responses -----------------

@response(Status.INTERNAL_SERVER_ERROR) case class InternalServerError[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object InternalServerError extends ResponseMeta[ErrorBody, InternalServerError[ErrorBody]]

@response(Status.NOT_IMPLEMENTED) case class NotImplemented[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object NotImplemented extends ResponseMeta[ErrorBody, NotImplemented[ErrorBody]]

@response(Status.BAD_GATEWAY) case class BadGateway[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object BadGateway extends ResponseMeta[ErrorBody, BadGateway[ErrorBody]]

@response(Status.SERVICE_UNAVAILABLE) case class ServiceUnavailable[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object ServiceUnavailable extends ResponseMeta[ErrorBody, ServiceUnavailable[ErrorBody]]

@response(Status.GATEWAY_TIMEOUT) case class GatewayTimeout[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object GatewayTimeout extends ResponseMeta[ErrorBody, GatewayTimeout[ErrorBody]]

@response(Status.HTTP_VERSION_NOT_SUPPORTED) case class HttpVersionNotSupported[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object HttpVersionNotSupported extends ResponseMeta[ErrorBody, HttpVersionNotSupported[ErrorBody]]

@response(Status.INSUFFICIENT_STORAGE) case class InsufficientStorage[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object InsufficientStorage extends ResponseMeta[ErrorBody, InsufficientStorage[ErrorBody]]
