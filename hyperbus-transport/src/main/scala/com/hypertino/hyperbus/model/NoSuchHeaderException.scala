package com.hypertino.hyperbus.model

/**
  * Created by maqdev on 1/20/17.
  */
class NoSuchHeaderException(header: String) extends RuntimeException(s"No such header: $header")
