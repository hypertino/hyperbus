package com.hypertino.hyperbus.model

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.{Null, Value}
import com.hypertino.hyperbus.model.hri.{HRIQueryConverter, PlainHRIQueryConverter}

case class HRI(@fieldName("a") serviceAddress: String,
               @fieldName("q") query: Value = Null) {

  def toURI(queryConverter: HRIQueryConverter = PlainHRIQueryConverter): String = {
    if (query.isNull) {
      serviceAddress
    }
    else {
      serviceAddress + queryConverter.toQueryString(query)
    }
  }
}

object HRI {
  def fromURI(uri: String, queryConverter: HRIQueryConverter): HRI = {
    val queryPos = uri.indexOf('?')
    if (queryPos > 0) {
      HRI(uri.substring(0, queryPos), queryConverter.parseQueryString(uri.substring(queryPos+1)))
    }
    else {
      HRI(uri)
    }
  }
}