/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport

import com.hypertino.binders.value.{Obj, _}
import com.hypertino.hyperbus.model.{Body, DynamicRequest, EmptyBody, HRL, Header, Headers, Method, RequestBase, RequestHeaders}
import com.hypertino.hyperbus.transport.api.matchers._
import org.scalatest.{FlatSpec, Matchers}
import com.hypertino.hyperbus.util.FuzzyIndexItemMetaInfo

class RequestMatcherSpec extends FlatSpec with Matchers {
  "RequestMatcher" should "match a Request with specific matcher" in {
    val requestMatcher = RequestMatcher(Map(Header.METHOD → Seq(Specific("get"))))

    requestMatcher.matches(
      request(Headers(Header.METHOD → "get"))
    ) shouldBe true

    requestMatcher.matches(
      request(Headers(Header.METHOD → "post"))
    ) shouldBe false
  }

  "RequestMatcher" should "return specific matchers as index properties" in {
    val requestMatcher = RequestMatcher(Map(Header.METHOD → Seq(Specific("get"))))
    requestMatcher.indexProperties should contain(FuzzyIndexItemMetaInfo(HeaderIndexKey(Header.METHOD, "get"), Header.METHOD))
  }

  "RequestMatcher" should "match a Request with RegEx matcher" in {
    val requestMatcher = RequestMatcher(Map(Header.METHOD → Seq(RegexMatcher("g.*"))))

    requestMatcher.matches(
      request(Headers(Header.METHOD → "get"))
    ) shouldBe true

    requestMatcher.matches(
      request(Headers(Header.METHOD → "post"))
    ) shouldBe false
  }

  "RequestMatcher" should "match a Request with inner header matcher" in {
    val requestMatcher = RequestMatcher(Map(
      "r.l" → Seq(Specific("hb://test")),
      "r.q.id" → Seq(Specific("100500")),
      "r.q.name" → Seq(RegexMatcher("M.*"))
    ))

    requestMatcher.matches(
      request(Headers("r" → Obj.from("l" → "hb://test", "q" → Obj.from("id" → 100500, "name" → "Maga"))))
    ) shouldBe true

    requestMatcher.matches(
      request(Headers(Header.METHOD → "post"))
    ) shouldBe false
  }

  "RequestMatcher" should "match a Request with PartMatcher" in {
    val fs = RequestMatcher(TextMatcher.fromCompactString("^f^/abcde"))
    fs.matches(request("/abcde")) shouldBe true
    fs.matches(request("/abcdE")) shouldBe false

    val fi = RequestMatcher(TextMatcher.fromCompactString("^fi^/abcde"))
    fi.matches(request("/abcde")) shouldBe true
    fi.matches(request("/abcdE")) shouldBe true
    fi.matches(request("/abcdex")) shouldBe false

    val ls = RequestMatcher(TextMatcher.fromCompactString("^l^/abcde"))
    ls.matches(request("/abcdef")) shouldBe true
    ls.matches(request("/abcdEf")) shouldBe false

    val li = RequestMatcher(TextMatcher.fromCompactString("^li^/abcde"))
    li.matches(request("/abcdef")) shouldBe true
    li.matches(request("/abcdEf")) shouldBe true
    li.matches(request("/abxcdex")) shouldBe false

    val rs = RequestMatcher(TextMatcher.fromCompactString("^r^/abcde"))
    rs.matches(request("qqq/abcdef")) shouldBe true
    rs.matches(request("qqq/abcdEf")) shouldBe false

    val ri = RequestMatcher(TextMatcher.fromCompactString("^ri^/abcde"))
    ri.matches(request("qqq/abcdef")) shouldBe true
    ri.matches(request("qqq/abcdEf")) shouldBe true
    ri.matches(request("qqq/abxcdex")) shouldBe false

    val ss = RequestMatcher(TextMatcher.fromCompactString("^s^/abcde/"))
    ss.matches(request("qqq/abcde/f")) shouldBe true
    ss.matches(request("qqq/abcdE/f")) shouldBe false

    val si = RequestMatcher(TextMatcher.fromCompactString("^si^/abcde/"))
    si.matches(request("qqq/abcde/f")) shouldBe true
    si.matches(request("qqq/abcdE/f")) shouldBe true
    si.matches(request("qqq/abxcd/ex")) shouldBe false
  }

  "RequestMatcher with content-type header" should "match a Request without a content-type header" in {
    val requestMatcher = RequestMatcher("hb://test", Method.POST, Some("test-body"))
    import com.hypertino.hyperbus.model.MessagingContext.Implicits.emptyContext
    requestMatcher.matches(
      DynamicRequest(HRL("hb://test"), Method.POST, EmptyBody)
    ) shouldBe true
  }

  "RegexMatcher" should "matchReplace" in {
    RegexMatcher("ab(.)de").replaceIfMatch("abcde", "ek$1lm") shouldBe Some("ekclm")
    RegexMatcher("ab(.)de").replaceIfMatch("ekclm", "ek$1lm") shouldBe None
  }

  def request(all: Headers): RequestBase = {
    new RequestBase{
      override def headers: RequestHeaders = RequestHeaders(all)
      override def body: Body = null
    }
  }

  def request(location: String, query: Value = Null): RequestBase = {
    new RequestBase{
      override def headers: RequestHeaders = RequestHeaders(
        Headers(Header.HRL → HRL(location, query).toValue)
      )
      override def body: Body = null
    }
  }
}
