package com.hypertino.hyperbus.transport

import java.io.{Reader, Writer}

import scala.language.experimental.macros

package object api {
  type Serializer[-T] = Function2[T, Writer, Unit]
  type Deserializer[+T] = Function1[Reader, T]
}
