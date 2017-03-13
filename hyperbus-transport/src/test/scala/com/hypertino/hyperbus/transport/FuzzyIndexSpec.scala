package com.hypertino.hyperbus.transport

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import com.hypertino.hyperbus.transport.api.matchers.{HeaderIndexKey, RequestMatcher, Specific}
import com.hypertino.hyperbus.util.{ComplexTrieMap, FuzzyIndex}
import monix.execution.Ack.{Continue, Stop}
import monix.execution.{Ack, Scheduler}
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.{ConcurrentSubject, PublishToOneSubject}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers, PrivateMethodTester}

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

class FuzzyIndexSpec extends FlatSpec with Matchers with PrivateMethodTester with ScalaFutures {
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

  "a" should "b" in {

    import monix.execution.Scheduler.Implicits.global

    val s = ConcurrentSubject.publishToOne[String]

    s.onNext("1").futureValue should equal(Continue)
    //println(s.isCanceled) // false

    val a = new AtomicInteger(0)
    val c = s.subscribe(new Subscriber[String] {
      override implicit def scheduler: Scheduler = monix.execution.Scheduler.Implicits.global
      override def onNext(elem: String): Future[Ack] = {
        println(s"next: $elem")
        if (a.incrementAndGet() < 2)
          Continue
        else
          Stop
      }
      override def onError(ex: Throwable): Unit = println(s"failed: $ex")
      override def onComplete(): Unit = println(s"complete")
    })

    s.onNext("2").futureValue should equal(Continue)
    s.onNext("3").futureValue should equal(Continue)
    //s.onComplete()
    //c.cancel()
    //println(s.isCanceled)
    Thread.sleep(1000)
    s.onNext("4").futureValue should equal(Stop)
    s.onNext("5").futureValue should equal(Stop)
  }
}
