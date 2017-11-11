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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Module

import scala.util.{Success, Try}


class HyperbusInprocTest extends FreeSpec with ScalaFutures with Matchers {
  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(10000, Millis)))
  implicit val mcx = MessagingContext("123")
  import testclasses._

  "Hyperbus " - {
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
  }

  def newHyperbus() = {
    implicit val injector = new Module {
      bind [Scheduler] to global
    }
    val tr = new InprocTransport
    val cr = List(ClientTransportRoute(tr, RequestMatcher.any))
    val sr = List(ServerTransportRoute(tr, RequestMatcher.any, DummyRegistrator))
    new Hyperbus(Some("group1"),
      readMessagesLogLevel = "TRACE", writeMessagesLogLevel = "DEBUG",
      serverReadMessagesLogLevel = "TRACE", serverWriteMessagesLogLevel = "DEBUG",
      cr, sr, global, injector)
  }
}
