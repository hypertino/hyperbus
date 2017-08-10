package com.hypertino.hyperbus.model

import org.scalatest.{FlatSpec, Matchers}

class HRLSpec extends FlatSpec with Matchers {
  "HRL" should "parse path" in {
    HRL("hb://service/path").path shouldBe "/path"
  }

  it should "parse path with params" in {
    HRL("hb://service/path/{param}").path shouldBe "/path/{param}"
  }

  it should "preserve path with encoded {" in {
    HRL("hb://service/path/%7Bparam%7D").path shouldBe "/path/%7Bparam%7D"
  }
}
