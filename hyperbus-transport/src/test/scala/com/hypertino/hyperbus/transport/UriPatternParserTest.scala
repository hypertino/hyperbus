package com.hypertino.hyperbus.transport

import com.hypertino.hyperbus.transport.api.matchers.Specific
import com.hypertino.hyperbus.transport.api.uri._
import org.scalatest.{FreeSpec, Matchers}

class UriPatternParserTest extends FreeSpec with Matchers {
  "UriParserTest " - {
    "Extract parameters" in {
      val p: String ⇒ Seq[String] = UriParser.extractParameters
      p("{abc}") should equal(Seq("abc"))
      p("/{abc}/") should equal(Seq("abc"))
      p("x/{abc}/y") should equal(Seq("abc"))
      p("x/{abc}/y/{def}") should equal(Seq("abc", "def"))
      p("{abc}{def}") should equal(Seq("abc", "def"))
    }

    "Parse URI" in {
      val p: String ⇒ Seq[Token] = UriParser.tokens
      p("{abc}") should equal(Seq(ParameterToken("abc")))
      p("/{abc}/") should equal(Seq(SlashToken, ParameterToken("abc"), SlashToken))
      p("x/{abc}/y") should equal(Seq(
        TextToken("x"), SlashToken, ParameterToken("abc"), SlashToken, TextToken("y"))
      )
      p("x/{abc}/y/{def}") should equal(Seq(
        TextToken("x"), SlashToken, ParameterToken("abc"),
        SlashToken, TextToken("y"), SlashToken, ParameterToken("def"))
      )
      p("{abc}{def}") should equal(Seq(ParameterToken("abc"), ParameterToken("def")))
      p("abcdef") should equal(Seq(TextToken("abcdef")))
      p("abc/def") should equal(Seq(TextToken("abc"), SlashToken, TextToken("def")))
      p("{abc:@}") should equal(Seq(ParameterToken("abc", RegularMatchType)))
      p("{abc:*}") should equal(Seq(ParameterToken("abc", PathMatchType)))
    }

    "UriPattern should match with params" in {
      val uriPattern = UriPattern("x/{abc}/y/{def}")
      val r = uriPattern.matchUri("x/1/y/2")
      r should not be empty
      r.get should contain theSameElementsAs Map("abc" → "1", "def" → "2")
    }

    "UriPattern shouldn't match with params" in {
      val uriPattern = UriPattern("x/{abc}/y/{def}")
      uriPattern.matchUri("x") shouldBe empty
    }

    "UriPattern match" in {
      val uriPattern = UriPattern("abc/xyz")
      val r = uriPattern.matchUri("abc/xyz")
      r should not be empty
      r.get shouldBe empty
    }

    "UriPattern shouldn't match" in {
      val uriPattern = UriPattern("abc/xyz")
      uriPattern.matchUri("x") shouldBe empty
    }

    "UriPattern match with path" in {
      val uriPattern = UriPattern("x/{abc:*}")
      val r = uriPattern.matchUri("x/1/y/2")
      r should not be empty
      r.get should contain theSameElementsAs Map("abc" → "1/y/2")
    }

    "UriPattern shouldn't match with path" in {
      val uriPattern = UriPattern("x/{abc:*}")
      uriPattern.matchUri("x") shouldBe empty
      uriPattern.matchUri("x/") shouldBe empty
    }
  }
}
