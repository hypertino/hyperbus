package com.hypertino.hyperbus.serialization

import java.io.{Reader, StringReader}

import com.fasterxml.jackson.core.{JsonFactory, JsonParser}
import com.hypertino.binders.json.{JacksonParserAdapter, JsonBindersFactory}
import com.hypertino.binders.value.{Obj, Text, Value}
import com.hypertino.hyperbus.model.{Body, Header, Headers, HeadersMap, Message}

object MessageReader {
  def read[M <: Message[_ <: Body,_ <: Headers]](reader: Reader, concreteDeserializer: MessageDeserializer[M]): M = {
    val jacksonFactory = new JsonFactory()
    jacksonFactory.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)

    val jp = jacksonFactory.createParser(reader)
    val headers = try {
      val adapter = new JacksonParserAdapter(jp)
      val headers = JsonBindersFactory.findFactory().withJsonParserApi(adapter) { jpa ⇒
        val headersSeq = jpa.unbind[Value].asInstanceOf[Obj].v.toSeq // todo: this isn't great, also see https://github.com/hypertino/binders/issues/2

        val transformedSeq = headersSeq.map {
          case (Header.CONTENT_TYPE, value) ⇒ Header.CONTENT_TYPE → JsonContentTypeConverter.universalJsonContentTypeToSimple(value)
          case other ⇒ other
        }

        HeadersMap(transformedSeq: _*)
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

  def fromString[M <: Message[_ <: Body,_ <: Headers]](message: String, concreteDeserializer: MessageDeserializer[M]): M = {
    val stringReader = new StringReader(message)
    try {
      read(stringReader, concreteDeserializer)
    }
    finally {
      stringReader.close()
    }
  }
}
