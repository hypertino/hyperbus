/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.util

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

// more unique than UUID & CPU hungry because of SecureRandom
// guarantees to grow monotonically until process is restarted
object IdGenerator extends IdGeneratorBase {
  private val random = new SecureRandom()
  private val counter = new AtomicInteger(random.nextInt(65536))

  def create(): String = {
    val sb = new StringBuilder(30)
    appendInt(sb, (System.currentTimeMillis() / 10000l & 0xFFFFFFFFl).toInt)
    appendInt(sb, counter.incrementAndGet())
    appendInt(sb, random.nextInt())
    appendInt(sb, random.nextInt())
    appendInt(sb, random.nextInt())
    appendInt(sb, random.nextInt())
    sb.toString()
  }
}
