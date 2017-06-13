package com.hypertino.hyperbus.model

object Header {
  val HRL = "r"
  val METHOD = "m"
  val CONTENT_TYPE = "t"
  val MESSAGE_ID = "i"
  val CORRELATION_ID = "c"
  val REVISION = "v"
  val STATUS_CODE = "s"
  val LOCATION = "l"
}

object HeaderHRL {
  val HRL = "l"
  val QUERY = "q"

  val FULL_HRL: String = Header.HRL + "." + HeaderHRL.HRL
  val FULL_QUERY: String = Header.HRL + "." + HeaderHRL.QUERY
}