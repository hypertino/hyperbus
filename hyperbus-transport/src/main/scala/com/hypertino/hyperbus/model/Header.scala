package com.hypertino.hyperbus.model

object Header {
  val HRI = "r"
  val METHOD = "m"
  val CONTENT_TYPE = "t"
  val MESSAGE_ID = "i"
  val CORRELATION_ID = "c"
  val REVISION = "v"
  val STATUS_CODE = "s"
}

object HeaderHRI {
  val SERVICE_ADDRESS = "a"
  val QUERY = "q"

  val FULL_SERVICE_ADDRESS: String = Header.HRI + "." + HeaderHRI.SERVICE_ADDRESS
  val FULL_QUERY: String = Header.HRI + "." + HeaderHRI.QUERY
}