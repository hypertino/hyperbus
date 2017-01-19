package com.hypertino.hyperbus

import com.hypertino.binders.value.Value

package object model {
  type Headers = Map[String, Value]
  type Links = Map[String, Either[Link, Seq[Link]]]
}
