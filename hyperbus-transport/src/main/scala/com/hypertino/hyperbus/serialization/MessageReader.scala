package com.hypertino.hyperbus.serialization

import java.io.{Reader, StringReader}

import com.hypertino.hyperbus.model.{Body, Headers, HeadersMap, Message}

object MessageReader {
  def apply[M <: Message[_ <: Body,_ <: Headers]](reader: Reader, concreteDeserializer: MessageDeserializer[M]): M = {
    import com.hypertino.binders.json.JsonBinders._
    implicit val bindOptions = com.hypertino.hyperbus.serialization.bindOptions
    val headers = reader.readJson[HeadersMap]
    concreteDeserializer(reader, headers)
  }

  def apply[M <: Message[_ <: Body,_ <: Headers]](message: String, concreteDeserializer: MessageDeserializer[M]): M = {
    val stringReader = new StringReader(message)
    try {
      apply(stringReader, concreteDeserializer)
    }
    finally {
      stringReader.close()
    }
  }
}
