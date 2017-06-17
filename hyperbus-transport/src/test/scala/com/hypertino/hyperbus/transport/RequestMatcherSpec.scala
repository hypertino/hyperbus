package com.hypertino.hyperbus.transport

import com.hypertino.binders.value.{Obj, _}
import com.hypertino.hyperbus.model.{Body, Header, HeadersMap, RequestBase, RequestHeaders}
import com.hypertino.hyperbus.transport.api.matchers.{HeaderIndexKey, RegexMatcher, RequestMatcher, Specific}
import org.scalatest.{FlatSpec, Matchers}
import com.hypertino.hyperbus.util.FuzzyIndexItemMetaInfo

class RequestMatcherSpec extends FlatSpec with Matchers {
  "RequestMatcher" should "match a Request with specific matcher" in {
    val requestMatcher = RequestMatcher(Map(Header.METHOD → Specific("get")))

    requestMatcher.matches(
      request(Map(Header.METHOD → "get"))
    ) shouldBe true

    requestMatcher.matches(
      request(Map(Header.METHOD → "post"))
    ) shouldBe false
  }

  "RequestMatcher" should "return specific matchers as index properties" in {
    val requestMatcher = RequestMatcher(Map(Header.METHOD → Specific("get")))
    requestMatcher.indexProperties should contain(FuzzyIndexItemMetaInfo(HeaderIndexKey(Header.METHOD, "get"), Header.METHOD))
  }

  "RequestMatcher" should "match a Request with RegEx matcher" in {
    val requestMatcher = RequestMatcher(Map(Header.METHOD → RegexMatcher("g.*")))

    requestMatcher.matches(
      request(Map(Header.METHOD → "get"))
    ) shouldBe true

    requestMatcher.matches(
      request(Map(Header.METHOD → "post"))
    ) shouldBe false
  }

  "RequestMatcher" should "match a Request with inner header matcher" in {
    val requestMatcher = RequestMatcher(Map("r.l" → Specific("hb://test")))

    requestMatcher.matches(
      request(Map("r" → Obj.from("l" → "hb://test")))
    ) shouldBe true

    requestMatcher.matches(
      request(Map(Header.METHOD → "post"))
    ) shouldBe false
  }

  def request(all: HeadersMap): RequestBase = {
    new RequestBase{
      override def headers: RequestHeaders = RequestHeaders(all)
      override def body: Body = null
    }
  }
}
