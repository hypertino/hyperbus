package com.hypertino.hyperbus.util

trait IdGeneratorBase {
  private val base64t = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz" // sorted by char code

  protected def appendInt(sb: StringBuilder, i: Int): Unit = {
    sb.append(base64t.charAt(i >> 24 & 63))
    sb.append(base64t.charAt(i >> 18 & 63))
    sb.append(base64t.charAt(i >> 12 & 63))
    sb.append(base64t.charAt(i >> 6 & 63))
    sb.append(base64t.charAt(i & 63))
  }
}
