package com.hypertino.hyperbus.transport.api.matchers

import java.util.concurrent.atomic.AtomicLong

import com.hypertino.binders.value.Lst
import com.hypertino.hyperbus.model.{Header, HeaderHRL, RequestBase}
import com.hypertino.hyperbus.util.{CanFuzzyMatchable, FuzzyIndexItemMetaInfo, FuzzyMatcher}
import com.typesafe.config.ConfigValue

import scala.collection.concurrent.TrieMap

case class HeaderIndexKey(path: String, value: String)

case class RequestMatcher(headers: Map[String, TextMatcher]) extends FuzzyMatcher {
  lazy val indexProperties: Seq[FuzzyIndexItemMetaInfo] = headers.collect {
    case (k, Specific(value)) ⇒ FuzzyIndexItemMetaInfo(HeaderIndexKey(k, value), k)
  }.toSeq

  protected lazy val pathsToMatcher: Seq[(Seq[String], TextMatcher)] = headers.toSeq.map{ case (k,v) ⇒
    k.split('.').toSeq -> v
  }

  def urlMatcher: Option[TextMatcher] = headers.get(HeaderHRL.FULL_HRL)
  def methodMatcher: Option[TextMatcher] = headers.get(Header.METHOD)
  def contentTypeMatcher: Option[TextMatcher] = headers.get(Header.CONTENT_TYPE)

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
      message.headers.byPath(path) match {
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

  def apply(urlMatcher: TextMatcher): RequestMatcher = new RequestMatcher(
    Map(HeaderHRL.FULL_HRL → urlMatcher)
  )

  def apply(url: String, method: String, contentType: Option[String]): RequestMatcher = {
    RequestMatcher(Map(
      HeaderHRL.FULL_HRL → Specific(url),
      Header.METHOD → Specific(method)) ++
      contentType.map(c ⇒ Header.CONTENT_TYPE → Specific(c))
    )
  }

  def apply(url: String, method: String): RequestMatcher = apply(url, method, None)

  def apply(config: ConfigValue): RequestMatcher = {
    import com.hypertino.binders.config.ConfigBinders._
    val headersPojo = config.read[Map[String, TextMatcherPojo]]
    apply(headersPojo.map { case (k, v) ⇒
      k → TextMatcher(v)
    })
  }

  implicit object RequestMatcherCanFuzzyMatchable extends CanFuzzyMatchable[RequestMatcher] {
    override def indexProperties(bloomFilter: TrieMap[Any, AtomicLong], a: RequestMatcher): Seq[Any] = a.indexProperties
  }
}

private[transport] case class RequestMatcherPojo(headers: Map[String, TextMatcherPojo])
