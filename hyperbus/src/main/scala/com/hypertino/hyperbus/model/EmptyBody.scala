/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model

import java.io.{Reader, Writer}

import com.hypertino.binders.value._
import com.hypertino.hyperbus.serialization.{DeserializeException, SerializationOptions}

trait EmptyBody extends DynamicBody {
  override def serialize(writer: Writer)(implicit so: SerializationOptions): Unit = writer.write("{}")
}

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = None
  def content = Null
  def apply(reader: Reader, contentType: Option[String]): EmptyBody = {
    val body = DynamicBody.apply(reader, contentType)
    if (body.content.isEmpty)
      EmptyBody
    else {
      throw DeserializeException(s"EmptyBody is expected, but got: '${body.content}'")
    }
  }
}
