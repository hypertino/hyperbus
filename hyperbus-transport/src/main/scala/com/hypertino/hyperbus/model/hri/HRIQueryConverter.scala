package com.hypertino.hyperbus.model.hri

import com.hypertino.binders.value.Value

trait HRIQueryConverter {
  def parseQueryString(queryString: String): Value
  def toQueryString(value: Value): String
}
