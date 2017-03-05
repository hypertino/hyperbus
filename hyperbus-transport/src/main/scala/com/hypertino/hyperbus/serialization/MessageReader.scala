package com.hypertino.hyperbus.serialization

import java.io.{Reader, StringReader}

import com.fasterxml.jackson.core.{JsonFactory, JsonParser}
import com.hypertino.binders.json.{JacksonParserAdapter, JsonBindersFactory}
import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.hyperbus.model.{Body, Headers, Message}

object MessageReader {
  def read[M <: Message[_ <: Body,_ <: Headers]](reader: Reader, concreteDeserializer: MessageDeserializer[M]): M = {
    val jacksonFactory = new JsonFactory()
    jacksonFactory.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)

    val jp = jacksonFactory.createParser(reader)
    val headers = try {
      val adapter = new JacksonParserAdapter(jp)
      val headers = JsonBindersFactory.findFactory().withJsonParserApi(adapter) { jpa ⇒
        jpa.unbind[Value].asInstanceOf[Obj]
      }

      jp.nextToken()
      val offset = jp.getTokenLocation.getCharOffset
      reader.reset()
      reader.skip(offset)
      headers
    }
    finally {
      jp.close()
    }

    concreteDeserializer(reader, headers)
  }

  def from[M <: Message[_ <: Body,_ <: Headers]](message: String, concreteDeserializer: MessageDeserializer[M]): M = {
    val stringReader = new StringReader(message)
    try {
      read(stringReader, concreteDeserializer)
    }
    finally {
      stringReader.close()
    }
  }
}
