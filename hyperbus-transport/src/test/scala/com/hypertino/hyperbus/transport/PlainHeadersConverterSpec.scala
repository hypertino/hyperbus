package com.hypertino.hyperbus.transport

import com.hypertino.binders.value.{Lst, Obj}
import com.hypertino.hyperbus.model.headers.PlainHeadersConverter
import org.scalatest.{FreeSpec, Matchers}

class PlainHeadersConverterSpec extends FreeSpec with Matchers {
  "PlainHeadersConverter " - {
    "Should convert headers to http/linear" in {
      PlainHeadersConverter.toHttp(Map("a" → "1", "b" → Lst.from("2","3"))) should equal (Seq("a" → "1", "b" → "2", "b" → "3"))
    }

    "Should generate http/linear headers to Obj" in {
      PlainHeadersConverter.fromHttp(Seq("a" → "1", "b" → "2", "b" → "3")) should equal (Obj.from("a" → "1", "b" → Lst.from("2","3")))
    }
  }
}
