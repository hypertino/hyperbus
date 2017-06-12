package com.hypertino.hyperbus.model.hri

import com.hypertino.binders.value.{Null, Obj, Text, Value}

object PlainHRIQueryConverter extends HRIQueryConverter {
  override def parseQueryString(queryString: String): Value = {
    if (queryString == null || queryString.isEmpty) {
      Null
    } else {

      Obj(queryString.split('&').map { s ⇒
        val i = s.indexOf('=')
        if (i>0) {
          val left = s.substring(0, i)
          val right = Text(s.substring(i+1))
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
          k + "=" + v.toString
        }
      } mkString "&"
      case other ⇒ throw new IllegalArgumentException(s"$other is not an Obj or Text")
    }
  }
}
