package com.hypertino.hyperbus.transport.api.matchers

import com.hypertino.binders.value.Lst
import com.hypertino.hyperbus.model.{Header, RequestBase}
import com.typesafe.config.ConfigValue

case class RequestMatcher(headers: Map[String, TextMatcher]) {
  def matchMessage(message: RequestBase): Boolean = {
    headers.map { case (headerName, headerMatcher) ⇒
      message.headers.map.get(headerName).map {
        case Lst(items) ⇒ items.exists(item ⇒ headerMatcher.matchText(Specific(item.toString)))
        case other ⇒ headerMatcher.matchText(Specific(other.toString))
      } getOrElse {
        false
      }
    }.forall(r => r)
  }

  // wide match for routing
  def matchRequestMatcher(other: RequestMatcher): Boolean = {
    headers.map { case (headerName, headerMatcher) ⇒
      other.headers.get(headerName).map { header ⇒
        headerMatcher.matchText(header)
      } getOrElse {
        false
      }
    }.forall(r => r)
  }
}

object RequestMatcher {
  val any = RequestMatcher(Any)

  def apply(uriMatcher: TextMatcher): RequestMatcher = new RequestMatcher(
    Map(Header.URI → uriMatcher)
  )

  private[transport] def apply(config: ConfigValue): RequestMatcher = {
    import com.hypertino.binders.config.ConfigBinders._
    val headersPojo = config.read[Map[String, TextMatcherPojo]]
    apply(headersPojo.map { case (k, v) ⇒
      k → TextMatcher(v)
    })
  }

//  private[transport] def apply(headersPojo: Map[String, TextMatcherPojo]): RequestMatcher = {
//    new RequestMatcher(headersPojo.map { case (k, v) ⇒
//      k → TextMatcher(v)
//    })
//  }
}

private[transport] case class RequestMatcherPojo(headers: Map[String, TextMatcherPojo])
