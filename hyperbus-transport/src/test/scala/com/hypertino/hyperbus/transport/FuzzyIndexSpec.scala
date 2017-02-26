package com.hypertino.hyperbus.transport

import java.util.concurrent.atomic.AtomicLong

import com.hypertino.hyperbus.transport.api.matchers.{HeaderIndexKey, RequestMatcher, Specific}
import com.hypertino.hyperbus.util.{ComplexTrieMap, FuzzyIndex}
import org.scalatest.{FlatSpec, Matchers, PrivateMethodTester}

import scala.collection.concurrent.TrieMap

class FuzzyIndexSpec extends FlatSpec with Matchers with PrivateMethodTester{
  "FuzzyIndex" should "index RequestMatcher properties" in {
    val fuzzyIndex = new FuzzyIndex[RequestMatcher]
    val requestMatcher = RequestMatcher(Map("m" → Specific("get")))
    fuzzyIndex.add(requestMatcher)

    val privateIndexValue = PrivateMethod[ComplexTrieMap[Any, Vector[RequestMatcher]]]('index)
    val index = fuzzyIndex invokePrivate privateIndexValue()

    index.get(HeaderIndexKey("m", "get")) should equal(Some(Vector(requestMatcher)))

    val privateBloomFilterValue = PrivateMethod[TrieMap[Any, TrieMap[Any, AtomicLong]]]('bloomFilter)
    val bloomFilter = fuzzyIndex invokePrivate privateBloomFilterValue()

    bloomFilter.get("m") shouldBe defined
  }
}
