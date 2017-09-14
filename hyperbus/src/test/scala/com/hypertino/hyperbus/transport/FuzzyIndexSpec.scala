package com.hypertino.hyperbus.transport

import java.util.concurrent.atomic.AtomicLong

import com.hypertino.hyperbus.model.{DynamicRequest, EmptyBody, HRL, MessagingContext, Method, RequestBase}
import com.hypertino.hyperbus.transport.api.matchers.{HeaderIndexKey, RequestMatcher, Specific}
import com.hypertino.hyperbus.util.{ComplexTrieMap, FuzzyIndex}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers, PrivateMethodTester}

import scala.collection.concurrent.TrieMap

class FuzzyIndexSpec extends FlatSpec with Matchers with PrivateMethodTester with ScalaFutures {
  "FuzzyIndex" should "index RequestMatcher properties" in {
    val fuzzyIndex = new FuzzyIndex[RequestMatcher]
    val requestMatcher = RequestMatcher(Map("m" → Seq(Specific("get"))))
    fuzzyIndex.add(requestMatcher)

    val privateIndexValue = PrivateMethod[ComplexTrieMap[Any, Vector[RequestMatcher]]]('index)
    val index = fuzzyIndex invokePrivate privateIndexValue()

    index.get(HeaderIndexKey("m", "get")) should equal(Some(Vector(requestMatcher)))

    val privateBloomFilterValue = PrivateMethod[TrieMap[Any, TrieMap[Any, AtomicLong]]]('bloomFilter)
    val bloomFilter = fuzzyIndex invokePrivate privateBloomFilterValue()

    bloomFilter.get("m") shouldBe defined
  }

  "FuzzyIndex" should "lookup by RequestMatcher properties" in {
    val fuzzyIndex = new FuzzyIndex[RequestMatcher]
    val requestMatcher = RequestMatcher(Map("m" → Seq(Specific("get"))))
    fuzzyIndex.add(requestMatcher)

    fuzzyIndex.lookupAll(requestMatcher) should equal(Seq(requestMatcher))
  }

  "FuzzyIndex" should "lookup by message if content-type in message is not specified" in {
    val fuzzyIndex = new FuzzyIndex[RequestMatcher]
    val requestMatcher = RequestMatcher("hb://test", Method.POST, Some("test-body"))
    fuzzyIndex.add(requestMatcher)
    implicit val mcx = MessagingContext.empty
    //implicit val observable = DynamicRequestObservableMeta(RequestMatcher("hb://test", Method.POST, None))
    val message: RequestBase = DynamicRequest(HRL("hb://test"), Method.POST, EmptyBody)
    fuzzyIndex.lookupAll(message) should equal(Seq(requestMatcher))
  }
}
