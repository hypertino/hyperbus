/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport

import com.hypertino.binders.value.{Lst, Obj}
import com.hypertino.hyperbus.model.Headers
import com.hypertino.hyperbus.model.headers.PlainHeadersConverter
import org.scalatest.{FreeSpec, Matchers}

class PlainHeadersConverterSpec extends FreeSpec with Matchers {
  "PlainHeadersConverter " - {
    "Should convert headers to http/linear" in {
      PlainHeadersConverter.toHttp(Headers("a" → "1", "b" → Lst.from("2","3"))) should equal (Seq("a" → "1", "b" → "2", "b" → "3"))
    }

    "Should generate http/linear headers to Obj" in {
      PlainHeadersConverter.fromHttp(Seq("a" → "1", "b" → "2", "b" → "3")) should equal (Headers("a" → "1", "b" → Lst.from("2","3")))
    }
  }
}
