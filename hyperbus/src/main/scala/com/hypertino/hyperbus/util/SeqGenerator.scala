/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.util

import java.security.SecureRandom

import monix.execution.atomic.AtomicInt

// don't tries to be securely unique
object SeqGenerator extends IdGeneratorBase {
  private val random = new SecureRandom()
  private val counter = AtomicInt(random.nextInt(65536))
  private val fixed = random.nextInt()

  def create(): String = {
    val sb = new StringBuilder(30)
    appendInt(sb, (System.currentTimeMillis() / 10000l & 0xFFFFFFFFl).toInt)
    appendInt(sb, fixed)
    appendInt(sb, counter.incrementAndGet())
    sb.toString()
  }
}