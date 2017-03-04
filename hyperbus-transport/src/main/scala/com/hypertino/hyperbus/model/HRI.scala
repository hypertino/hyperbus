package com.hypertino.hyperbus.model

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.{Null, Value}

case class HRI(@fieldName("a") serviceAddress: String,
               @fieldName("q") query: Value = Null)