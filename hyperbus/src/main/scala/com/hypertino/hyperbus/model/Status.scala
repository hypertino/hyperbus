/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model

object Status {
  final val OK = 200
  final val CREATED = 201
  final val ACCEPTED = 202
  final val NON_AUTHORITATIVE_INFORMATION = 203
  final val NO_CONTENT = 204
  final val RESET_CONTENT = 205
  final val PARTIAL_CONTENT = 206
  final val MULTI_STATUS = 207

  final val MULTIPLE_CHOICES = 300
  final val MOVED_PERMANENTLY = 301
  final val FOUND = 302
  final val SEE_OTHER = 303
  final val NOT_MODIFIED = 304
  final val USE_PROXY = 305
  final val TEMPORARY_REDIRECT = 307

  final val BAD_REQUEST = 400
  final val UNAUTHORIZED = 401
  final val PAYMENT_REQUIRED = 402
  final val FORBIDDEN = 403
  final val NOT_FOUND = 404
  final val METHOD_NOT_ALLOWED = 405
  final val NOT_ACCEPTABLE = 406
  final val PROXY_AUTHENTICATION_REQUIRED = 407
  final val REQUEST_TIMEOUT = 408
  final val CONFLICT = 409
  final val GONE = 410
  final val LENGTH_REQUIRED = 411
  final val PRECONDITION_FAILED = 412
  final val REQUEST_ENTITY_TOO_LARGE = 413
  final val REQUEST_URI_TOO_LONG = 414
  final val UNSUPPORTED_MEDIA_TYPE = 415
  final val REQUESTED_RANGE_NOT_SATISFIABLE = 416
  final val EXPECTATION_FAILED = 417
  final val UNPROCESSABLE_ENTITY = 422
  final val LOCKED = 423
  final val FAILED_DEPENDENCY = 424
  final val TOO_MANY_REQUESTS = 429

  final val INTERNAL_SERVER_ERROR = 500
  final val NOT_IMPLEMENTED = 501
  final val BAD_GATEWAY = 502
  final val SERVICE_UNAVAILABLE = 503
  final val GATEWAY_TIMEOUT = 504
  final val HTTP_VERSION_NOT_SUPPORTED = 505
  final val INSUFFICIENT_STORAGE = 507

  final val nameToStatusCode = Map(
    "ok" → OK,
    "created" → CREATED,
    "accepted" → ACCEPTED,
    "non_authoritative_information" → NON_AUTHORITATIVE_INFORMATION,
    "reset_content" → RESET_CONTENT,
    "partial_content" → PARTIAL_CONTENT,
    "multi_status" → MULTI_STATUS,
    "multiple_choices" → MULTIPLE_CHOICES,
    "moved_permanently" → MOVED_PERMANENTLY,
    "found" → FOUND,
    "see_other" → SEE_OTHER,
    "not_modified" → NOT_MODIFIED,
    "use_proxy" → USE_PROXY,
    "temporary_redirect" → TEMPORARY_REDIRECT,
    "bad_request" → BAD_REQUEST,
    "unauthorized" → UNAUTHORIZED,
    "payment_required" → PAYMENT_REQUIRED,
    "forbidden" → FORBIDDEN,
    "not_found" → NOT_FOUND,
    "method_not_allowed" → METHOD_NOT_ALLOWED,
    "not_acceptable" → NOT_ACCEPTABLE,
    "proxy_authentication_required" → PROXY_AUTHENTICATION_REQUIRED,
    "request_timeout" → REQUEST_TIMEOUT,
    "conflict" → CONFLICT,
    "gone" → GONE,
    "length_required" → LENGTH_REQUIRED,
    "precondition_failed" → PRECONDITION_FAILED,
    "request_entity_too_large" → REQUEST_ENTITY_TOO_LARGE,
    "request_uri_too_long" → REQUEST_URI_TOO_LONG,
    "unsupported_media_type" → UNSUPPORTED_MEDIA_TYPE,
    "requested_range_not_satisfiable" → REQUESTED_RANGE_NOT_SATISFIABLE,
    "expectation_failed" → EXPECTATION_FAILED,
    "unprocessable_entity" → UNPROCESSABLE_ENTITY,
    "locked" → LOCKED,
    "failed_dependency" → FAILED_DEPENDENCY,
    "too_many_requests" → TOO_MANY_REQUESTS,

    "internal_server_error" → INTERNAL_SERVER_ERROR,
    "not_implemented" → NOT_IMPLEMENTED,
    "bad_gateway" → BAD_GATEWAY,
    "service_unavailable" → SERVICE_UNAVAILABLE,
    "gateway_timeout" → GATEWAY_TIMEOUT,
    "http_version_not_supported" → HTTP_VERSION_NOT_SUPPORTED,
    "insufficient_storage" → INSUFFICIENT_STORAGE
  )

  final val statusCodeToName = nameToStatusCode.map(kv ⇒ kv._2 → kv._1)
}