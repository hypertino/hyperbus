package com.hypertino.hyperbus.model.hrl

import com.hypertino.binders.value.Value

trait QueryConverter {
  def parseQueryString(queryString: String): Value
  def toQueryString(value: Value): String
}
