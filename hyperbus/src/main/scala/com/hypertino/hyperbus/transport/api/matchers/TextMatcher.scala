package com.hypertino.hyperbus.transport.api.matchers

import com.typesafe.config.ConfigValue
import com.hypertino.binders.annotations.fieldName
import com.hypertino.hyperbus.config.HyperbusConfigurationError

import scala.util.matching.Regex

sealed trait TextMatcher {
  def matchText(other: TextMatcher): Boolean

  def specific: String = this match {
    case Specific(value) ⇒ value
    case _ ⇒ throw new UnsupportedOperationException(s"Specific value expected but got $getClass")
  }
}

object TextMatcher {
  def apply(value: Option[String], matchType: Option[String]): TextMatcher = matchType match {
    case Some("Any") ⇒ Any
    case Some("Regex") ⇒ RegexMatcher(value.getOrElse(
      throw new HyperbusConfigurationError("Please provide value for Regex matcher"))
    )
    case Some("Specific") | None ⇒ Specific(value.getOrElse(
      throw new HyperbusConfigurationError("Please provide value for Specific matcher"))
    )
    case other ⇒
      throw new HyperbusConfigurationError(s"Unsupported TextMatcher: $other")
  }

  def fromCompactString(v: String): TextMatcher = {
    v match {
      case "\\*" ⇒ Specific("*")                                  // '\*' = *
      case "*" ⇒ Any                                              // '*' = Any
      case _ if v.startsWith("\\~") ⇒ Specific(v.substring(1))    // \~... = ~...
      case _ if v.startsWith("\\\\") ⇒ Specific(v.substring(1))   // \\... = \...
      case _ if v.startsWith("~") ⇒ RegexMatcher(v.substring(1))  //
      case other ⇒ Specific(other)
    }
  }

  implicit def apply(v: String): Specific = Specific(v)
  implicit def apply(r: Regex): RegexMatcher = RegexMatcher(r)
}

case object Any extends TextMatcher {
  def matchText(other: TextMatcher) = true
}

case class RegexMatcher(valueRegex: Regex) extends TextMatcher {
  def matchText(other: TextMatcher) = other match {
    case Specific(otherValue) ⇒ valueRegex.findFirstMatchIn(otherValue).isDefined
    case RegexMatcher(otherRegexValue) ⇒ otherRegexValue == valueRegex
    case _ ⇒ false
  }
}

object RegexMatcher {
  def apply(value: String): RegexMatcher = RegexMatcher(new Regex(value))
}

case class Specific(value: String) extends TextMatcher {
  def matchText(other: TextMatcher) = other match {
    case Specific(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchText(this)
  }
}

// todo: + ignore case flag, StartsWith, EndsWith

// private[api] case class TextMatcherPojo(value: Option[String], @fieldName("type") matchType: Option[String])
