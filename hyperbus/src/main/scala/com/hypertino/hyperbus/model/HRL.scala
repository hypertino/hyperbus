package com.hypertino.hyperbus.model

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.{Null, Value}
import com.hypertino.hyperbus.model.hrl.{PlainQueryConverter, QueryConverter}
import com.netaporter.uri.Uri
import com.netaporter.uri.config.UriConfig
import com.netaporter.uri.encoding.PercentEncoder

case class HRL(@fieldName("l") location: String,
               @fieldName("q") query: Value = Null) {

  def scheme: Option[String] = _uri.scheme
  def service: Option[String] = _uri.host
  def port: Option[Int] = _uri.port
  def path: String = _uri.pathRaw
  def authority: String = {
    scheme.map { s ⇒
      s + "://" + service.getOrElse("???")
    } getOrElse {
      service.map { s ⇒
        "//" + s
      }.getOrElse("")
    } + port.map { p ⇒
      ":" + p
    }.getOrElse {
      ""
    }
  }

  private lazy val _uri: com.netaporter.uri.Uri = HRL.parseUri(location)

  def toURL(queryConverter: QueryConverter = PlainQueryConverter): String = {
    if (query.isNull) {
      location
    }
    else {
      location + "?" + queryConverter.toQueryString(query)
    }
  }
}

object HRL {
  import com.netaporter.uri.encoding.PercentEncoder.PATH_CHARS_TO_ENCODE

  private val HRL_PATH_CHARS_TO_ENCODE = PATH_CHARS_TO_ENCODE -- Set ('{','}')
  private implicit val uriConfig = UriConfig.default.copy(pathEncoder = PercentEncoder(HRL_PATH_CHARS_TO_ENCODE))

  def fromURL(url: String, queryConverter: QueryConverter = PlainQueryConverter): HRL = {
    val queryPos = url.indexOf('?')
    if (queryPos > 0) {
      HRL(url.substring(0, queryPos), queryConverter.parseQueryString(url.substring(queryPos+1)))
    }
    else {
      HRL(url)
    }
  }

  private [model] def parseUri(uri: String): Uri = Uri.parse(uri)
}

