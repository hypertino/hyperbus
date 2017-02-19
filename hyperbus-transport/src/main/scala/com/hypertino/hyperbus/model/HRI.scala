package com.hypertino.hyperbus.model

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.Value

case class HRI(@fieldName(HeaderHRI.SERVICE_ADDRESS) serviceAddress: String,
               @fieldName(HeaderHRI.QUERY) query: Value)
