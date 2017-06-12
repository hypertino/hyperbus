package com.hypertino.hyperbus.transport

import com.hypertino.binders.value.{Null, Obj}
import com.hypertino.hyperbus.model.hri.PlainHRIQueryConverter
import org.scalatest.{FreeSpec, Matchers}

class HRIQueryConverterSpec extends FreeSpec with Matchers {
  "HRIConverter " - {
    "Should parse queryString" in {
      PlainHRIQueryConverter.parseQueryString("a=1&b=2&c&d=") should equal(Obj.from("a" → "1", "b" → "2", "c" → Null, "d" → ""))
    }

    "Should generate queryString" in {
      PlainHRIQueryConverter.toQueryString(Obj.from("a" → "1", "b" → "2", "c" → Null, "d" → "")) should equal("a=1&b=2&c&d=")
    }
  }
}
