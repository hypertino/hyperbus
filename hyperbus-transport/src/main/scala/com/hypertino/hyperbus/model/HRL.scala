package com.hypertino.hyperbus.model

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.{Null, Value}
import com.hypertino.hyperbus.model.hrl.{QueryConverter, PlainQueryConverter}

case class HRL(@fieldName("l") location: String,
               @fieldName("q") query: Value = Null) {

  def toURL(queryConverter: QueryConverter = PlainQueryConverter): String = {
    if (query.isNull) {
      location
    }
    else {
      location + "?" + queryConverter.toQueryString(query)
    }
  }
}

object HRL {
  def fromURL(url: String, queryConverter: QueryConverter): HRL = {
    val queryPos = url.indexOf('?')
    if (queryPos > 0) {
      HRL(url.substring(0, queryPos), queryConverter.parseQueryString(url.substring(queryPos+1)))
    }
    else {
      HRL(url)
    }
  }
}