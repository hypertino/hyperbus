package com.hypertino.hyperbus.serialization

class HeaderIsRequiredException(header: String) extends RuntimeException(s"Header value is not set: $header")
