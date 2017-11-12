/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model

object Header {
  val HRL = "r"
  val METHOD = "m"
  val CONTENT_TYPE = "t"
  val MESSAGE_ID = "i"
  val CORRELATION_ID = "c"
  val PARENT_ID = "p"
  val REVISION = "v"
  val STATUS_CODE = "s"
  val LOCATION = "l"
  val COUNT = "n"
  val LINK = "k"

  val fullNameMap = Map(
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
  val LOCATION = "l"
  val QUERY = "q"

  val FULL_HRL: String = Header.HRL + "." + HeaderHRL.LOCATION
  val FULL_QUERY: String = Header.HRL + "." + HeaderHRL.QUERY
}