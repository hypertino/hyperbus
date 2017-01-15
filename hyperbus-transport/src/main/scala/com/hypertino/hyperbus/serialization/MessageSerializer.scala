package com.hypertino.hyperbus.serialization

import java.io.Writer

import com.hypertino.binders.core.BindOptions
import com.hypertino.hyperbus.model.{Body, Request, Response}
import com.hypertino.hyperbus.transport.api.uri.UriJsonSerializer

object MessageSerializer {

  import com.hypertino.binders.json.JsonBinders._
  implicit val bindOptions = new BindOptions(true)
  implicit val uriJsonSerializer = new UriJsonSerializer

  def serializeRequest[B <: Body](request: Request[B], writer: Writer): Unit = {
    require(request.headers, "method")
    require(request.headers, "messageId")
    //val req = request.uri, request.headers)
    writer.write("""{"uri":""")
    request.uri.writeJson(writer)
    writer.write(""","headers":""")
    request.headers.writeJson(writer)
    writer.write(""","body":""")
    request.body.serialize(writer)
    writer.write("}")
  }

  def serializeResponse[B <: Body](response: Response[B], writer: Writer): Unit = {
    require(response.headers, "messageId")
    //val resp = ResponseHeader(response.statusCode, response.headers)
    writer.write("""{"status":""")
    response.statusCode.writeJson(writer)
    writer.write(""","headers":""")
    response.headers.writeJson(writer)
    writer.write(""","body":""")
    response.body.serialize(writer)
    writer.write("}")
  }

  private def require(headers: Map[String, Seq[String]], required: String) = {
    if (headers.get(required).flatMap(_.headOption).isEmpty)
      throw new HeaderIsRequiredException(required)
  }
}

