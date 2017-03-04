package com.hypertino.hyperbus.transport.api.matchers

import com.hypertino.binders.value.Lst
import com.hypertino.hyperbus.model.{Header, HeaderHRI, RequestBase}
import com.hypertino.hyperbus.util.{FuzzyIndexItemMetaInfo, FuzzyMatcher}
import com.typesafe.config.ConfigValue

case class HeaderIndexKey(path: String, value: String)

case class RequestMatcher(headers: Map[String, TextMatcher]) extends FuzzyMatcher {
  lazy val indexProperties: Seq[FuzzyIndexItemMetaInfo] = headers.collect {
    case (k, Specific(value)) ⇒ FuzzyIndexItemMetaInfo(HeaderIndexKey(k, value), k)
  }.toSeq

  protected lazy val pathsToMatcher: Seq[(Seq[String], TextMatcher)] = headers.toSeq.map{ case (k,v) ⇒
    k.split('.').toSeq -> v
  }

  def matches(other: Any): Boolean = {
    other match {
      case request: RequestBase ⇒ matchMessage(request)
      case matcher: RequestMatcher ⇒ matchRequestMatcher(matcher)
      case _ ⇒ false
    }
  }

  def matchMessage(message: RequestBase): Boolean = {
    import com.hypertino.hyperbus.model._
    pathsToMatcher.forall { case (path, matcher) ⇒
      message.headers.all.byPath(path) match {
        case Lst(items) ⇒ items.exists(item ⇒ matcher.matchText(Specific(item.toString)))
        case other ⇒ matcher.matchText(Specific(other.toString))
      }
    }
  }

  // wide match for routing
  def matchRequestMatcher(other: RequestMatcher): Boolean = {
    headers.forall { case (headerName, headerMatcher) ⇒
      other.headers.get(headerName).map { header ⇒
        headerMatcher.matchText(header)
      } getOrElse {
        false
      }
    }
  }
}

object RequestMatcher {
  val any = RequestMatcher(Any)

  def apply(serviceAddressMatcher: TextMatcher): RequestMatcher = new RequestMatcher(
    Map(HeaderHRI.FULL_SERVICE_ADDRESS → serviceAddressMatcher)
  )

  def apply(serviceAddress: String, method: String, contentType: Option[String]): RequestMatcher = {
    RequestMatcher(Map(
      HeaderHRI.FULL_SERVICE_ADDRESS → Specific(serviceAddress),
      Header.METHOD → Specific(method)) ++
      contentType.map(c ⇒ Header.CONTENT_TYPE → Specific(c))
    )
  }

  def apply(serviceAddress: String, method: String): RequestMatcher = apply(serviceAddress, method, None)

  private[transport] def apply(config: ConfigValue): RequestMatcher = {
    import com.hypertino.binders.config.ConfigBinders._
    val headersPojo = config.read[Map[String, TextMatcherPojo]]
    apply(headersPojo.map { case (k, v) ⇒
      k → TextMatcher(v)
    })
  }
}

private[transport] case class RequestMatcherPojo(headers: Map[String, TextMatcherPojo])
