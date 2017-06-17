package com.hypertino.hyperbus.serialization

import com.hypertino.binders.value.{Null, Text, Value}

/*
  This translation is performed on a serialization level
*/

object JsonContentTypeConverter {
  val CERTAIN_CONTENT_TYPE_START  = "application/vnd."
  val CERTAIN_CONTENT_TYPE_END    = "+json"
  val COMMON_CONTENT_TYPE         = "application/json"

  def universalJsonContentTypeToSimple(httpContentType: Value): Value = {
    httpContentType match {
      case Null | Text(COMMON_CONTENT_TYPE) ⇒ Null
      case Text(s) if (s.startsWith(CERTAIN_CONTENT_TYPE_START)
        && s.endsWith(CERTAIN_CONTENT_TYPE_END)) ⇒
        val beginIndex = CERTAIN_CONTENT_TYPE_START.length
        val endIndex = s.length - CERTAIN_CONTENT_TYPE_END.length
        val r = s.substring(beginIndex, endIndex)
        if (r.isEmpty)
          Null
        else
          Text(r)

      case other ⇒ other
    }
  }

  def localContentTypeToUniversalJson(contentType: Value): Value = {
    contentType match {
      case Null ⇒ Null
      case Text(s) ⇒ Text(CERTAIN_CONTENT_TYPE_START + s + CERTAIN_CONTENT_TYPE_END)
      case other ⇒ other
    }
  }
}
