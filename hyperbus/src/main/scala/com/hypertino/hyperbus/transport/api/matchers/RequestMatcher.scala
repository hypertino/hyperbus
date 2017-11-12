/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport.api.matchers

import java.util.concurrent.atomic.AtomicLong

import com.hypertino.binders.value.{Lst, Null, Obj, Text, Value}
import com.hypertino.hyperbus.config.HyperbusConfigurationError
import com.hypertino.hyperbus.model.{Header, HeaderHRL, RequestBase}
import com.hypertino.hyperbus.util.{CanFuzzyMatchable, FuzzyIndexItemMetaInfo, FuzzyMatcher}
import com.typesafe.config.ConfigValue

import scala.collection.concurrent.TrieMap

case class HeaderIndexKey(path: String, value: String)

case class RequestMatcher(headers: Map[String, Seq[TextMatcher]]) extends FuzzyMatcher {
  lazy val indexProperties: Seq[FuzzyIndexItemMetaInfo] = headers.toSeq.flatMap { case (k, v) ⇒
    v.flatMap {
      case Specific(value) ⇒ Some(FuzzyIndexItemMetaInfo(HeaderIndexKey(k, value), k))
      case _ ⇒ None
    }
  }

  protected lazy val pathsToMatcher: Seq[(Seq[String], Seq[TextMatcher])] = headers.toSeq.map{ case (k,v) ⇒
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
    pathsToMatcher.forall { case (path, matchers) ⇒
      message.headers.byPath(path) match {
        case Null ⇒ matchers.exists(m ⇒ m == Any || m == EmptyMatcher)
        case Lst(items) ⇒ items.exists(item ⇒ matchers.exists(_.matchText(Specific(item.toString))))
        case other ⇒ matchers.exists(_.matchText(Specific(other.toString)))
      }
    }
  }

  // wide match for routing
  def matchRequestMatcher(other: RequestMatcher): Boolean = {
    headers.forall { case (headerName, headerMatchers) ⇒
      other.headers.get(headerName).map { otherMatchers ⇒
        headerMatchers.exists(hm ⇒ otherMatchers.exists(hm.matchText))
      } getOrElse {
        false
      }
    }
  }
}

object RequestMatcher {
  val any = RequestMatcher(Any)

  def apply(urlMatcher: TextMatcher): RequestMatcher = new RequestMatcher(
    Map(HeaderHRL.FULL_HRL → Seq(urlMatcher))
  )

  def apply(url: String, method: String, contentType: Option[String]): RequestMatcher = {
    RequestMatcher(Map(
      HeaderHRL.FULL_HRL → Seq(Specific(url)),
      Header.METHOD → Seq(Specific(method))) ++
      contentType.map(c ⇒ Header.CONTENT_TYPE → Seq(Specific(c), EmptyMatcher))
    )
  }

  def apply(url: String, method: String): RequestMatcher = apply(url, method, None)

  def apply(config: ConfigValue): RequestMatcher = {
    import com.hypertino.binders.config.ConfigBinders._
    val obj = config.read[Value].asInstanceOf[Obj]
    apply(recursive("", obj).toMap)
  }

  private def recursive(path: String, obj: Obj): Seq[(String, Seq[TextMatcher])] = {
    obj.v.toSeq.flatMap {
      case (s, inner: Obj) ⇒ recursive(path + s + ".", inner)
      case (s, other: Text) ⇒ Seq((path + s, Seq(TextMatcher.fromCompactString(other.v))))
      case (s, other: Lst) ⇒ Seq((path + s, other.v.map(t ⇒ TextMatcher.fromCompactString(t.toString))))
      case other ⇒ throw new HyperbusConfigurationError(s"Can't use $other to initialize RequestMatcher")
    }
  }

  implicit object RequestMatcherCanFuzzyMatchable extends CanFuzzyMatchable[RequestMatcher] {
    override def indexProperties(bloomFilter: TrieMap[Any, AtomicLong], a: RequestMatcher): Seq[Any] = a.indexProperties
  }
}
