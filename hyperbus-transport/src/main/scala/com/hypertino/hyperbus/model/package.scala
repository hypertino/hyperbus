package com.hypertino.hyperbus

import java.util.concurrent.atomic.AtomicLong

import com.hypertino.binders.value._
import com.hypertino.hyperbus.transport.api.matchers.HeaderIndexKey
import com.hypertino.hyperbus.util.CanFuzzyMatchable

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

package object model {
  type HeadersMap = Map[String, Value]
  type Links = Map[String, Either[Link, Seq[Link]]]
  type RequestBase = Request[Body]
  type ResponseBase = Response[Body]

  implicit def requestToMessageContext(requestBase: RequestBase): MessagingContext = {
    MessagingContext(requestBase.headers.correlationId)
  }

  object HeadersMap {
    def empty = Map.empty[String, Value]
    def apply(elems: (String, Value)*): HeadersMap = (scala.collection.mutable.LinkedHashMap[String, Value]() ++= elems).toMap
  }

  implicit class HeadersMapWrapper(val h: HeadersMap) extends AnyVal {
    def safe(name: String): Value = h.getOrElse(name, throw new NoSuchHeaderException(name))
    def byPath(path: Seq[String]): Value = h.getOrElse(path.head, Null)(path.tail)
  }

  implicit class ValueWrapper(val v: Value) extends AnyVal {
    def apply(path: Seq[String]): Value = PathSelector.inner(path, v)
  }

  private [model] object PathSelector {
    def inner(path: Seq[String], v: Value): Value = if (path.isEmpty) v else {
      inner(path.tail, v.selectDynamic(path.head))
    }
  }

  implicit object RequestBaseCanFuzzyMatchable extends CanFuzzyMatchable[RequestBase] {
    override def indexProperties(bloomFilter: TrieMap[Any, AtomicLong], a: RequestBase): Seq[Any] = {
      val r = mutable.MutableList[Any]()
      a.headers.all.foreach { case (k, v) ⇒
        appendIndexProperties(bloomFilter, k, v, r)
      }
      r
    }

    private def appendIndexProperties(bloomFilter: TrieMap[Any, AtomicLong],
                                      path: String,
                                      v: Value,
                                      to: mutable.MutableList[Any]): Unit = {
      if (bloomFilter.contains(path)) {
        v match {
          case Text(s) ⇒ to += HeaderIndexKey(path, s)
          case Lst(lst) ⇒ to += HeaderIndexKey(path, lst.size.toString)
          case Obj(map) ⇒ to += HeaderIndexKey(path, map.size.toString)
          case other ⇒ to += HeaderIndexKey(path, other.toString)
        }
      }

      v match {
        case Lst(lst) ⇒ lst.foreach(item ⇒ appendIndexProperties(bloomFilter, path + "[]", item, to))
        case Obj(map) ⇒ map.foreach(item ⇒ appendIndexProperties(bloomFilter, path + "." + item._1, item._2, to))
        case _ ⇒ // ignore
      }
    }
  }
}
