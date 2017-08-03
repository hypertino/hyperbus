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
  val COUNT = "n"
  val LINK = "k"

  val fullNameMap = Map(
    HRL → "HRL",
    METHOD → "METHOD",
    CONTENT_TYPE → "CONTENT_TYPE",
    MESSAGE_ID → "MESSAGE_ID",
    CORRELATION_ID → "CORRELATION_ID",
    REVISION → "REVISION",
    STATUS_CODE → "STATUS_CODE",
    LOCATION → "LOCATION"
  )
}

object HeaderHRL {
  val LOCATION = "l"
  val QUERY = "q"

  val FULL_HRL: String = Header.HRL + "." + HeaderHRL.LOCATION
  val FULL_QUERY: String = Header.HRL + "." + HeaderHRL.QUERY
}