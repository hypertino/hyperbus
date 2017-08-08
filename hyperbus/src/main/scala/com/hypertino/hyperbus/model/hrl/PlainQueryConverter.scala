package com.hypertino.hyperbus.model.hrl

import java.net.{URLDecoder, URLEncoder}

import com.hypertino.binders.value.{Null, Obj, Text, Value}

object PlainQueryConverter extends QueryConverter {
  final val encoding = "UTF-8"

  override def parseQueryString(queryString: String): Value = {
    if (queryString == null || queryString.isEmpty) {
      Null
    } else {

      Obj(queryString.split('&').map { s ⇒
        val i = s.indexOf('=')
        if (i>0) {
          val left = s.substring(0, i)
          val right = Text(URLDecoder.decode(s.substring(i+1), encoding))
          left → right
        }
        else {
          s → Null
        }
      }.toMap)
    }
  }

  override def toQueryString(value: Value): String = {
    value match {
      case Null ⇒ ""
      case Text(s) ⇒ s
      case Obj(elements) ⇒ elements.map { case (k, v) ⇒
        if (v.isNull) {
          k
        }
        else {
          k + "=" + URLEncoder.encode(v.toString, encoding)
        }
      } mkString "&"
      case other ⇒ throw new IllegalArgumentException(s"$other is not an Obj or Text")
    }
  }
}
