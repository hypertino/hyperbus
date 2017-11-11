package com.hypertino.hyperbus.transport.api.matchers

import com.hypertino.hyperbus.config.HyperbusConfigurationError
import org.apache.commons.lang3.StringUtils

import scala.util.matching.Regex

sealed trait TextMatcher {
  def matchText(other: TextMatcher): Boolean
}

object TextMatcher {

  def fromCompactString(v: String): TextMatcher = {
    v match {
      case _ if v.startsWith("^") ⇒
        val next = v.substring(1)
        val endOfOptions = next.indexOf("^")
        if (endOfOptions > 0) {
          val options = next.substring(0, endOfOptions)
          fromCompactStringWithOptions(next.substring(endOfOptions+1), Some(options))
        }
        else {
          Specific(v)
        }
      case other ⇒
        fromCompactStringWithOptions(other, None)
    }
  }

  private def fromCompactStringWithOptions(v: String, options: Option[String]): TextMatcher = {
    v match {
      case "\\*" ⇒ specificOrPartMatcher("*", options)                                  // '\*' = *
      case "*" ⇒ Any                                                                    // '*' = Any
      case _ if v.startsWith("\\~") ⇒ specificOrPartMatcher(v.substring(1), options)    // \~... = ~...
      case _ if v.startsWith("\\\\") ⇒ specificOrPartMatcher(v.substring(1), options)   // \\... = \...
      case _ if v.startsWith("\\^") ⇒ specificOrPartMatcher(v.substring(1), options)    // \\... = \...
      case _ if v.startsWith("~") ⇒ RegexMatcher(v.substring(1))  //
      case other ⇒ specificOrPartMatcher(other, options)
    }
  }

  private def specificOrPartMatcher(value: String, options: Option[String]): TextMatcher = {
    if (options.isEmpty || options.get.isEmpty) {
      Specific(value)
    }
    else {
      PartMatcher(value, options.get)
    }
  }

  implicit def apply(v: String): Specific = Specific(v)
}

case object EmptyMatcher extends TextMatcher {
  def matchText(other: TextMatcher) = other match {
    case Specific(otherValue) ⇒ otherValue.isEmpty
    case EmptyMatcher ⇒ true
    case _ ⇒ false
  }
}

case object Any extends TextMatcher {
  def matchText(other: TextMatcher) = true
}

case class RegexMatcher(value: String) extends TextMatcher {
  lazy val valueRegex = new Regex(value)
  def matchText(other: TextMatcher) = other match {
    case Specific(otherValue) ⇒ valueRegex.findFirstMatchIn(otherValue).isDefined
    case RegexMatcher(otherRegexPattern) ⇒ otherRegexPattern == value
    case _ ⇒ false
  }
}

case class Specific(value: String) extends TextMatcher {
  def matchText(other: TextMatcher) = other match {
    case Specific(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchText(this)
  }
}

case class PartMatcher(value: String, part: Int, ignoreCase: Boolean) extends TextMatcher {
  override def matchText(other: TextMatcher): Boolean = other match {
    case Specific(s) ⇒
      part match {
        case PartMatcher.LEFT ⇒ {
          if (ignoreCase)
            StringUtils.indexOfIgnoreCase(s, value)
          else
            StringUtils.indexOf(s, value)
          } == 0

        case PartMatcher.RIGHT ⇒ {
          if (ignoreCase)
                StringUtils.lastIndexOfIgnoreCase(s, value)
              else
                StringUtils.lastIndexOf(s, value)
          } == s.length - value.length - 1

        case PartMatcher.SUBSTRING ⇒ {
          if (ignoreCase)
            StringUtils.indexOfIgnoreCase(s, value)
          else
            StringUtils.indexOf(s, value)
        } >= 0

        case PartMatcher.FULL ⇒ {
          if (ignoreCase)
            StringUtils.compareIgnoreCase(s, value)
          else
            StringUtils.compare(s, value)
        } == 0
      }

    case o: PartMatcher ⇒ o == this
    case o ⇒ o.matchText(this)
  }
}

object PartMatcher {
  final val LEFT = 1
  final val RIGHT = 2
  final val SUBSTRING = 3
  final val FULL = 4

  def apply(value: String, options: String): PartMatcher = {
    var part: Int = FULL
    var ignoreCase = false
    options.foreach {
      case 'i' ⇒ ignoreCase = true
      case 'l' ⇒ part = LEFT
      case 'r' ⇒ part = RIGHT
      case 's'⇒ part = SUBSTRING
      case 'f'⇒ part = FULL
    }
    PartMatcher(value, part, ignoreCase)
  }
}
