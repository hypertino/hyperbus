package com.hypertino.hyperbus.transport

import java.util.concurrent.atomic.AtomicInteger

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.model.annotations.{body, request, response}
import com.hypertino.hyperbus.transport.api.{NoTransportRouteException, _}
import com.hypertino.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.{Ack, Scheduler}
import monix.reactive.observers.Subscriber
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._

@body("mock")
case class MockBody(test: String) extends Body

@request(Method.POST, "hb://mock")
case class MockRequest(body: MockBody) extends Request[MockBody]

@response(Status.OK)
case class MockResponse[+B <: MockBody](body: B) extends Response[B]

object MockResponse extends ResponseMeta[MockBody, MockResponse[MockBody]]


class InprocTransportTest extends FreeSpec with ScalaFutures with Matchers with Eventually {
  import monix.execution.Scheduler.Implicits.global
  implicit val mcx = MessagingContext("123")

  //todo: add test for: + handler exception, decoder exception

  "InprocTransport " - {
    "Simple Test" in {
      val t = new InprocTransport
      t.commands(RequestMatcher(Specific("hb://mock")), null).subscribe(requestProcessor())

      val f = t.ask(MockRequest(MockBody("hey")), null).asInstanceOf[Task[MockResponse[MockBody]]]
      f.runAsync.futureValue.body.test should equal("yeh")
    }

    "NoTransportRouteException Test" in {
      val t = new InprocTransport
      t.commands(RequestMatcher(Specific("notexists")), null).subscribe(requestProcessor())

      val f = t.ask(MockRequest(MockBody("hey")), null).asInstanceOf[Task[MockResponse[MockBody]]]
      f.runAsync.failed.futureValue shouldBe a[NoTransportRouteException]
    }

    "Complex ask Test (Service and Subscribers)" in {
      val t = new InprocTransport
      t.commands(RequestMatcher(Specific("hb://mock")), null).subscribe(requestProcessor())

      val (s1, gc1events, gc1complete) = eventSubscriber()
      t.events(RequestMatcher(Specific("hb://mock")), "group1", null).subscribe(s1)
      t.events(RequestMatcher(Specific("hb://mock")), "group1", null).subscribe(s1)
      t.events(RequestMatcher(Specific("hb://mock")), "group1", null).subscribe(s1)

      val (s2, gc2events, gc2complete) = eventSubscriber()
      t.events(RequestMatcher(Specific("hb://mock")), "group2", null).subscribe(s2)
      t.events(RequestMatcher(Specific("hb://mock")), "group2", null).subscribe(s2)

      val f = t.ask(MockRequest(MockBody("hey")), null).asInstanceOf[Task[MockResponse[MockBody]]]
      f.runAsync.futureValue.body.test should equal("yeh")

      t.shutdown(1.second)

      eventually {
        gc1events.get() should equal(1)
        gc2events.get() should equal(1)
        gc1complete.get should equal(3)
        gc2complete.get should equal(2)
      }
    }

    "Complex publish Test (Service and Subscribers)" in {
      val t = new InprocTransport

      val counter = new AtomicInteger(0)
      t.commands(RequestMatcher(Specific("hb://mock")), null).subscribe(requestProcessor(counter))
      t.commands(RequestMatcher(Specific("hb://mock")), null).subscribe(requestProcessor(counter))

      val (s1, gc1events, gc1complete) = eventSubscriber()
      t.events(RequestMatcher(Specific("hb://mock")), "group1", null).subscribe(s1)
      t.events(RequestMatcher(Specific("hb://mock")), "group1", null).subscribe(s1)
      t.events(RequestMatcher(Specific("hb://mock")), "group1", null).subscribe(s1)

      val (s2, gc2events, gc2complete) = eventSubscriber()
      t.events(RequestMatcher(Specific("hb://mock")), "group2", null).subscribe(s2)
      t.events(RequestMatcher(Specific("hb://mock")), "group2", null).subscribe(s2)

      val f: Task[PublishResult] = t.publish(MockRequest(MockBody("hey")))
      val publishResult = f.runAsync.futureValue
      publishResult.sent should equal(Some(true))
      publishResult.offset should equal(None)

      eventually {
        counter.get() should equal(1)
        gc1events.get() should equal(1)
        gc2events.get() should equal(1)
      }

      t.shutdown(1.second)

      eventually {
        gc1complete.get should equal(3)
        gc2complete.get should equal(2)
      }
    }

    "Test Subscribers" in {
      val t = new InprocTransport

      val (s1, gc1events, gc1complete) = eventSubscriber()
      t.events(RequestMatcher(Specific("hb://mock")), "group1", null).subscribe(s1)
      t.events(RequestMatcher(Specific("hb://mock")), "group1", null).subscribe(s1)
      t.events(RequestMatcher(Specific("hb://mock")), "group1", null).subscribe(s1)

      val (s2, gc2events, gc2complete) = eventSubscriber()
      t.events(RequestMatcher(Specific("hb://mock")), "group2", null).subscribe(s2)
      t.events(RequestMatcher(Specific("hb://mock")), "group2", null).subscribe(s2)

      val f: Task[PublishResult] = t.publish(MockRequest(MockBody("hey")))
      val publishResult = f.runAsync.futureValue
      publishResult.sent should equal(Some(true))
      publishResult.offset should equal(None)

      eventually {
        gc1events.get() should equal(1)
        gc2events.get() should equal(1)
      }

      t.shutdown(1.second)

      eventually {
        gc1complete.get should equal(3)
        gc2complete.get should equal(2)
      }
    }

    "Test Receivers" in {
      val t = new InprocTransport
      val counter = new AtomicInteger(0)
      t.commands(RequestMatcher(Specific("hb://mock")), null).subscribe(requestProcessor(counter))
      t.commands(RequestMatcher(Specific("hb://mock")), null).subscribe(requestProcessor(counter))


      val f1 =
        t.ask(MockRequest(MockBody("hey")), null).asInstanceOf[Task[MockResponse[MockBody]]]

      val f2 =
        t.ask(MockRequest(MockBody("hey your")), null).asInstanceOf[Task[MockResponse[MockBody]]]

      val f3 =
        t.ask(MockRequest(MockBody("yo")), null).asInstanceOf[Task[MockResponse[MockBody]]]

      f1.runAsync.futureValue.body.test should equal("yeh")
      f2.runAsync.futureValue.body.test should equal("ruoy yeh")
      f3.runAsync.futureValue.body.test should equal("oy")
      counter.get() should equal(3)
    }

    "Unsubscribe Test" in {
      val t = new InprocTransport

      val c1 = t.commands(RequestMatcher(Specific("hb://mock")), null).subscribe(requestProcessor())
      val c2 = t.commands(RequestMatcher(Specific("hb://mock")), null).subscribe(requestProcessor())

      val f1 = t.ask(MockRequest(MockBody("hey")), null).asInstanceOf[Task[MockResponse[MockBody]]]
      f1.runAsync.futureValue.body.test should equal("yeh")

      c1.cancel()

      val f2 = t.ask(MockRequest(MockBody("yo")), null).asInstanceOf[Task[MockResponse[MockBody]]]
      f2.runAsync.futureValue.body.test should equal("oy")

      c2.cancel()

      val f3: Task[_] = t.ask(MockRequest(MockBody("ma")), null)
      f3.runAsync.failed.futureValue shouldBe a[NoTransportRouteException]
    }

    def eventSubscriber(): (Subscriber[RequestBase], AtomicInteger, AtomicInteger) = {
      val c1 = new AtomicInteger()
      val c2 = new AtomicInteger()
      val c3 = new AtomicInteger()
      val s = new Subscriber[RequestBase] {
        override implicit def scheduler: Scheduler = monix.execution.Scheduler.Implicits.global
        override def onNext(elem: RequestBase): Future[Ack] = {
          c1.incrementAndGet()
          Continue
        }
        override def onError(ex: Throwable): Unit = {
          c3.incrementAndGet()
        }
        override def onComplete(): Unit = {
          c2.incrementAndGet()
        }
      }
      (s, c1, c2)
    }

    def requestProcessor(counter: AtomicInteger = new AtomicInteger(0)): Subscriber[CommandEvent[MockRequest]] = {
      val s = new Subscriber[CommandEvent[MockRequest]] {
        override implicit def scheduler: Scheduler = monix.execution.Scheduler.Implicits.global
        override def onNext(elem: CommandEvent[MockRequest]): Future[Ack] = {
          elem.responsePromise.success(
            MockResponse(MockBody(elem.request.body.test.reverse))
          )
          counter.incrementAndGet()
          Continue
        }
        override def onError(ex: Throwable): Unit = {
          //c3.incrementAndGet()
        }
        override def onComplete(): Unit = {
          //c2.incrementAndGet()
        }
      }
      s
    }
  }
}
