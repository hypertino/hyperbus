package com.hypertino.hyperbus.transport.api.matchers

import org.apache.commons.lang3.StringUtils

import scala.util.matching.Regex

// todo: test if it's possible to make Specifc AnyVal without GC when matchText is called

sealed trait TextMatcher extends Any {
  def matchText(other: TextMatcher): Boolean
  def matchText(other: Specific): Boolean = matchText(other: TextMatcher) // GC optimization
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
    case s: Specific ⇒ matchText(s)
    case EmptyMatcher ⇒ true
    case _ ⇒ false
  }
  override def matchText(other: Specific): Boolean = other.value.isEmpty
}

case object Any extends TextMatcher {
  def matchText(other: TextMatcher) = true
}

case class RegexMatcher(valueRegex: Regex) extends TextMatcher {
  def matchText(other: TextMatcher) = other match {
    case s: Specific ⇒ matchText(s)
    case RegexMatcher(otherRegexPattern) ⇒ otherRegexPattern.pattern.toString == valueRegex.pattern.toString
    case _ ⇒ false
  }
  override def matchText(other: Specific): Boolean = valueRegex.findFirstMatchIn(other.value).isDefined
  def replaceIfMatch(s: String, replacement: String): Option[String] = {
    val m = valueRegex.pattern.matcher(s)
    var result = m.find
    if (result) {
      val sb = new StringBuffer
      do {
        m.appendReplacement(sb, replacement)
        result = m.find
      } while ( {
        result
      })
      m.appendTail(sb)
      Some(sb.toString)
    }
    else {
      None
    }
  }

  override def hashCode(): Int = valueRegex.pattern.pattern().hashCode
  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case r: RegexMatcher ⇒ r.valueRegex.pattern.pattern() == valueRegex.pattern.pattern()
      case _ ⇒ false
    }
  }
}

object RegexMatcher {
  def apply(pattern: String): RegexMatcher = RegexMatcher(new Regex(pattern))
}

case class Specific(value: String) extends AnyVal with TextMatcher {
  def matchText(other: TextMatcher) = other match {
    case s: Specific ⇒ matchText(s)
    case _ ⇒ other.matchText(this)
  }
  override def matchText(other: Specific): Boolean = other.value == value
}

case class PartMatcher(value: String, part: Int, ignoreCase: Boolean) extends TextMatcher {
  override def matchText(other: TextMatcher): Boolean = other match {
    case s: Specific ⇒ matchText(s)
    case o: PartMatcher ⇒ o == this
    case o ⇒ o.matchText(this)
  }
  override def matchText(other: Specific): Boolean = {
    val s = other.value
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
