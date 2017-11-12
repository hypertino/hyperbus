/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport

import com.hypertino.hyperbus.util.IdGenerator
import org.scalatest.{FreeSpec, Matchers}

class IdGeneratorSpec extends FreeSpec with Matchers {
  "IdGenerator " - {
    "Should generate sorted sequence" in {

      val list = 0 until 5000 map { i ⇒ IdGenerator.create() }
      list.sortBy(l ⇒ l) should equal(list) // sequence is sorted
      list.foreach { l ⇒
        list.count(_ == l) should equal(1) //evey one is unique
        l.length == 30
      }
    }
  }
}
