/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model

object Header {
  final val HRL = "r"
  final val METHOD = "m"
  final val CONTENT_TYPE = "t"
  final val MESSAGE_ID = "i"
  final val CORRELATION_ID = "c"
  final val PARENT_ID = "p"
  final val REVISION = "v"
  final val STATUS_CODE = "s"
  final val LOCATION = "l"
  final val COUNT = "n"
  final val LINK = "k"

  final val fullNameMap = Map(
    HRL → "HRL",
    METHOD → "METHOD",
    CONTENT_TYPE → "CONTENT_TYPE",
    MESSAGE_ID → "MESSAGE_ID",
    PARENT_ID → "PARENT_ID",
    CORRELATION_ID → "CORRELATION_ID",
    REVISION → "REVISION",
    STATUS_CODE → "STATUS_CODE",
    LOCATION → "LOCATION",
    COUNT → "COUNT",
    LINK → "LINK"
  )
}

object HeaderHRL {
  final val LOCATION = "l"
  final val QUERY = "q"

  final val FULL_HRL: String = Header.HRL + "." + HeaderHRL.LOCATION
  final val FULL_QUERY: String = Header.HRL + "." + HeaderHRL.QUERY
}