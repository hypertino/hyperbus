/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport

import com.hypertino.binders.value.{Null, Obj}
import com.hypertino.hyperbus.model.hrl.PlainQueryConverter
import org.scalatest.{FreeSpec, Matchers}

class PlainQueryConverterSpec extends FreeSpec with Matchers {
  "PlainQueryConverter " - {
    "Should parse queryString" in {
      PlainQueryConverter.parseQueryString("a=1&b=2&c&d=") should equal(Obj.from("a" → "1", "b" → "2", "c" → Null, "d" → ""))
    }

    "Should generate queryString" in {
      PlainQueryConverter.toQueryString(Obj.from("a" → "1", "b" → "2", "c" → Null, "d" → "")) should equal("a=1&b=2&c&d=")
    }
  }
}
