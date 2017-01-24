package com.hypertino.hyperbus.transport.api.uri

import scala.collection.{immutable, mutable}
import scala.language.postfixOps

case class UriPattern(pattern: String) {
  lazy val tokens: Seq[Token] = skipConsecutiveSlashes(UriParser.tokens(pattern))
  lazy val params: Set[String] = tokens.collect {
    case ParameterToken(str, _) ⇒ str
  } toSet

  override def toString = s"UriPattern($pattern$paramsFormat)"

  private[this] def paramsFormat =
    if (params.isEmpty) ""
    else
      params.mkString("#", ",", "")


  def matchUri(uri: String): Option[Map[String,String]] = {
    val uriTokens = skipConsecutiveSlashes(UriParser.tokens(uri))
    val it = uriTokens.iterator
    val resultBuilder = immutable.Map.newBuilder[String, String]
    val matches = tokens.forall { t ⇒
      if (it.hasNext) {
        val next = it.next()
        t match {
          case ParameterToken(name, RegularMatchType) ⇒
            resultBuilder += name → stringify(next)
            true
          case ParameterToken(name, PathMatchType) ⇒
            val pathBuilder = new StringBuilder
            pathBuilder.append(stringify(next))
            while(it.hasNext) {
              pathBuilder.append(stringify(it.next))
            }
            resultBuilder += name → pathBuilder.result()
            true
          case other ⇒ other == next
        }
      }
      else {
        false
      }
    }
    if (matches && !it.hasNext) {
      Some(resultBuilder.result())
    }
    else {
      None
    }
  }

  def formatUri(arguments: Map[String, String]): String = {
    val result = new StringBuilder
    tokens.foreach {
      case ParameterToken(parameterName, _) ⇒
        result.append(arguments(parameterName))
      case other ⇒
        result.append(stringify(other))
    }
    result.toString()
  }

  private def stringify(t: Token) = {
    t match {
      case SlashToken ⇒ "/"
      case TextToken(s) ⇒ s
      case ParameterToken(value, _) ⇒ "{???" + value + "???}" // todo: this shouldn't happen for a valid uri
    }
  }

  private def skipConsecutiveSlashes(tokens: Seq[Token]): Seq[Token] = {
    tokens.head +: ((tokens zip tokens.tail) collect {
      case (a,b) if a != b => b
      case (_,b) if b != SlashToken ⇒ b
    })
  }
}

