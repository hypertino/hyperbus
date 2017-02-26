package com.hypertino.hyperbus.transport

import com.hypertino.binders.value.{Obj, ObjV}
import com.hypertino.hyperbus.model.{Body, HeadersMap, RequestBase, RequestHeaders}
import com.hypertino.hyperbus.transport.api.matchers.{HeaderIndexKey, RegexMatcher, RequestMatcher, Specific}
import org.scalatest.{FlatSpec, Matchers}
import com.hypertino.binders.value._
import com.hypertino.hyperbus.util.FuzzyIndexItemMetaInfo

class RequestMatcherSpec extends FlatSpec with Matchers {
  "RequestMatcher" should "match a Request with specific matcher" in {
    val requestMatcher = RequestMatcher(Map("m" → Specific("get")))

    requestMatcher.matches(
      request(HeadersMap("m" → "get"))
    ) shouldBe true

    requestMatcher.matches(
      request(HeadersMap("m" → "post"))
    ) shouldBe false
  }

  "RequestMatcher" should "return specific matchers as index properties" in {
    val requestMatcher = RequestMatcher(Map("m" → Specific("get")))
    requestMatcher.indexProperties should contain(FuzzyIndexItemMetaInfo(HeaderIndexKey("m", "get"), "m"))
  }

  "RequestMatcher" should "match a Request with RegEx matcher" in {
    val requestMatcher = RequestMatcher(Map("m" → RegexMatcher("g.*")))

    requestMatcher.matches(
      request(HeadersMap("m" → "get"))
    ) shouldBe true

    requestMatcher.matches(
      request(HeadersMap("m" → "post"))
    ) shouldBe false
  }

  "RequestMatcher" should "match a Request with inner header matcher" in {
    val requestMatcher = RequestMatcher(Map("r.a" → Specific("hb://test")))

    requestMatcher.matches(
      request(HeadersMap("r" → ObjV("a" → "hb://test")))
    ) shouldBe true

    requestMatcher.matches(
      request(HeadersMap("m" → "post"))
    ) shouldBe false
  }

  def request(all: HeadersMap): RequestBase = {
    new RequestBase{
      override def headers: RequestHeaders = RequestHeaders(all)
      override def body: Body = null
    }
  }
}
