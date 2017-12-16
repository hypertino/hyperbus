/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport



import com.hypertino.binders.value.{Null, Text}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.transport.registrators.DummyRegistrator
import monix.execution.Ack.Continue
import monix.execution.Scheduler
import monix.execution.Scheduler.Implicits.global
import monix.execution.atomic.AtomicInt
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Span}
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Module

import scala.util.{Success, Try}


class HyperbusInprocTest extends FreeSpec with ScalaFutures with Matchers with Eventually {
  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(12000, Millis)))
  implicit val mcx = MessagingContext("123")
  import testclasses._

  "Hyperbus Inproc" - {
    "Send and Receive" in {
      val hyperbus = newHyperbus()

      hyperbus.commands[TestPost1].subscribe{ implicit c ⇒
        c.reply(Success {
          Created(testclasses.TestCreatedBody("100500"))
        })
        Continue
      }

      hyperbus.commands[TestPost2].subscribe{ implicit c ⇒
        c.reply(Success {
          Created(TestCreatedBody(c.request.body.resourceData.toString))
        })
        Continue
      }

      hyperbus.commands[TestPostWith2Responses].subscribe{ implicit c ⇒
        c.reply(Success {
          Ok(TestAnotherBody(c.request.body.resourceData.reverse))
        })
        Continue
      }

      val f1 = hyperbus ask TestPost1(testclasses.TestBody1("ha ha")) runAsync

      f1.futureValue.body should equal(testclasses.TestCreatedBody("100500"))

      val f2 = hyperbus ask TestPost2(TestBody2(7890)) runAsync

      f2.futureValue.body should equal(testclasses.TestCreatedBody("7890"))

      val f3 = hyperbus ask TestPostWith2Responses(testclasses.TestBody1("Yey")) runAsync

      f3.futureValue.body should equal(TestAnotherBody("yeY"))

      val f4 = hyperbus ask SomeContentPut("/test", DynamicBody(Null)) runAsync // this should compile, don't remove

      f4.failed.futureValue shouldBe a [NoTransportRouteException]
    }

    "Send and Receive multiple responses" in {
      val hyperbus = newHyperbus()

      hyperbus.commands[TestPost3].subscribe{ implicit c ⇒
        c.reply(Try.apply[ResponseBase]{
          val post = c.request
          if (post.body.resourceData == 1)
            Created(testclasses.TestCreatedBody("100500"))
          else if (post.body.resourceData == -1)
            throw Conflict(ErrorBody("failed"))
          else if (post.body.resourceData == -2)
            Conflict(ErrorBody("failed"))
          else
            Ok(DynamicBody(Text("another result")))
        })
        Continue
      }

      val f = hyperbus ask TestPost3(TestBody2(1)) runAsync

      val r = f.futureValue
      r shouldBe a[Created[_]]
      r.body should equal(testclasses.TestCreatedBody("100500"))

      val f2 = hyperbus ask TestPost3(TestBody2(2)) runAsync
      val r2 = f2.futureValue
      r2 shouldBe a[Ok[_]]
      r2.body should equal(DynamicBody(Text("another result")))

      val f3 = hyperbus ask TestPost3(TestBody2(-1)) runAsync

      f3.failed.futureValue shouldBe a[Conflict[_]]

      val f4 = hyperbus ask TestPost3(TestBody2(-2)) runAsync

      f4.failed.futureValue shouldBe a[Conflict[_]]
    }

    "Publish events" in {
      val hyperbus = newHyperbus()

      val c1 = AtomicInt(0)
      val c2 = AtomicInt(0)

      val s1g1 = hyperbus.events[TestPost1](Some("g1")).subscribe { implicit c ⇒
        c1.increment()
        Continue
      }

      val s2g1 = hyperbus.events[TestPost1](Some("g1")).subscribe { implicit c ⇒
        c1.increment()
        Continue
      }

      val s1g2 = hyperbus.events[TestPost1](Some("g2")).subscribe { implicit c ⇒
        c2.increment()
        Continue
      }

      val f = hyperbus publish TestPost1(TestBody1("abc")) runAsync
      val r = f.futureValue
      r shouldBe a[Seq[_]]

      eventually {
        c1.get shouldBe 1
        c2.get shouldBe 1
      }
      for (i ← 0 until 100) {
        val f2 = hyperbus publish TestPost1(TestBody1("abc")) runAsync
        val r2 = f2.futureValue
        r2 shouldBe a[Seq[_]]
      }

      eventually {
        c1.get shouldBe 101
        c2.get shouldBe 101
      }
    }

    "Publish events when handler is failed should not stop handling further events" in {
      val hyperbus = newHyperbus()

      val c1 = AtomicInt(0)
      val c2 = AtomicInt(0)

      val s1g1 = hyperbus.events[TestPost1](Some("g1")).subscribe { implicit c ⇒
        hyperbus.safeHandleEvent(c) { _ ⇒
          val i = c1.getAndIncrement()
          if (i == 0) {
            throw new RuntimeException(s"call #$i failed")
          }
          Continue
        }
      }

      val s1g2 = hyperbus.events[TestPost1](Some("g2")).subscribe { implicit c ⇒
        c2.increment()
        Continue
      }

      val f = hyperbus publish TestPost1(TestBody1("abc")) runAsync
      val r = f.futureValue
      r shouldBe a[Seq[_]]

      eventually {
        c1.get shouldBe 1
        c2.get shouldBe 1
      }

      val f2 = hyperbus publish TestPost1(TestBody1("abc")) runAsync
      val r2 = f2.futureValue
      r2 shouldBe a[Seq[_]]

      eventually {
        c1.get shouldBe 2
        c2.get shouldBe 2
      }
    }
  }

  def newHyperbus() = {
    implicit val injector = new Module {
      bind [Scheduler] to global
    }
    val tr = new InprocTransport
    val cr = List(ClientTransportRoute(tr, RequestMatcher.any, None))
    val sr = List(ServerTransportRoute(tr, RequestMatcher.any, DummyRegistrator, None))
    new Hyperbus(Some("group1"),
      readMessagesLogLevel = "TRACE", writeMessagesLogLevel = "DEBUG",
      serverReadMessagesLogLevel = "TRACE", serverWriteMessagesLogLevel = "DEBUG",
      cr, sr, global, injector)
  }
}
