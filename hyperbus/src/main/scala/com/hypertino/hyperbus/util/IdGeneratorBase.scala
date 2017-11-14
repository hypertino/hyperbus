/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.util

trait IdGeneratorBase {
  final protected val base64t = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz" // sorted by char code

  protected def appendInt(sb: StringBuilder, i: Int): Unit = {
    sb.append(base64t.charAt(i >> 24 & 63))
    sb.append(base64t.charAt(i >> 18 & 63))
    sb.append(base64t.charAt(i >> 12 & 63))
    sb.append(base64t.charAt(i >> 6 & 63))
    sb.append(base64t.charAt(i & 63))
  }
}
