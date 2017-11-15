import com.hypertino.hyperbus.model.{InternalServerError, MessagingContext, ResponseBase}
import com.hypertino.hyperbus.util.ErrorUtils
import monix.eval.Task
import org.scalatest.{FlatSpec, Matchers}

import scala.util.{Success, Try}

/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

class ErrorUtilsSpec extends FlatSpec with Matchers {
  implicit val mcx = MessagingContext.empty
  import monix.execution.Scheduler.Implicits.global

  "ErrorUtils.unexpected" should "match all unmatched" in {
    val v: Try[Any] = Success("abc")
    intercept [InternalServerError[_]] {
      v match {
        case Success(true) ⇒ fail()
        case o ⇒ ErrorUtils.unexpected(o)
      }
    }
  }

  "ErrorUtils.unexpected partial function" should "match all unmatched" in {
    val v: Try[Any] = Success("abc")
    val pf: PartialFunction[Any, ResponseBase] = {
      case Success(true) ⇒ fail()
    }

    val r = pf orElse ErrorUtils.unexpected
    intercept [InternalServerError[_]] (r(v))
  }

  "ErrorUtils.unexpectedTask" should "match all unmatched" in {
    val v: Try[Any] = Success("abc")
    val r = v match {
      case Success(true) ⇒ fail()
      case o ⇒ ErrorUtils.unexpectedTask(o)
    }
    intercept [InternalServerError[_]] (r.runSyncMaybe)
  }

  "ErrorUtils.unexpectedTask partial function" should "match all unmatched" in {
    val v: Try[Any] = Success("abc")
    val pf: PartialFunction[Any, Task[ResponseBase]] = {
      case Success(true) ⇒ Task.now(fail())
    }

    val r = pf orElse ErrorUtils.unexpectedTask
//    r(v) shouldBe a[InternalServerError[_]]
    intercept [InternalServerError[_]] (r(v).runSyncMaybe)
  }
}
