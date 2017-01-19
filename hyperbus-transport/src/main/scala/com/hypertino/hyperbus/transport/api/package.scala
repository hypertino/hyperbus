package com.hypertino.hyperbus.transport

import java.io.{Reader, Writer}

import com.hypertino.binders.value.Value

import scala.language.experimental.macros

package object api {
  type Serializer[-T] = (T, Writer) ⇒ Unit
  type Deserializer[+T] = (Reader) ⇒ T

  type Headers = Map[String, Value]
}
