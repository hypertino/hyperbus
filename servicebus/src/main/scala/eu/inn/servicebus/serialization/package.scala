package eu.inn.servicebus

import java.io.{InputStream, OutputStream}
import scala.language.experimental.macros

package object serialization {
  type Encoder[T] = Function2[T, OutputStream, Unit]
  type Decoder[T] = Function1[InputStream, T]

  def createDecoder[T]: Decoder[T] = macro JsonSerializationMacro.createDecoder[T]
  def createEncoder[T]: Encoder[T] = macro JsonSerializationMacro.createEncoder[T]
}