package com.hypertino.hyperbus.util

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import scala.collection.concurrent.TrieMap

trait CanFuzzyMatchable[A] {
  def indexProperties(bloomFilter: TrieMap[Any, AtomicLong], a: A): Seq[Any]
}

case class FuzzyIndexItemMetaInfo(indexValue: Any, bloomFilterValue: Any)

trait FuzzyMatcher {
  def indexProperties: Seq[FuzzyIndexItemMetaInfo]
  def matches(other: Any): Boolean
}

class CanFuzzyIndex[A] extends CanComplexElement[Vector[A]] {
  def upsert(existing: Vector[A], upsert: Vector[A]): Vector[A] = existing ++ upsert
  def remove[A2](existing: Vector[A], remove: A2): Vector[A] = existing.filterNot(_ == remove)
  def isEmpty(existing: Vector[A]): Boolean = existing.isEmpty
}

class FuzzyIndex[A <: FuzzyMatcher] {
  private implicit val evidence = new CanFuzzyIndex[A]
  private val index = new ComplexTrieMap[Any, Vector[A]]()
  private val bloomFilter = TrieMap[Any, AtomicLong]()

  def add(a: A): Unit = {
    val v = Vector(a)
    a.indexProperties.foreach { meta ⇒
      index.upsert(meta.indexValue, v)
    }
    index.upsert(All, v)
    synchronized {
      a.indexProperties.foreach { meta ⇒
        bloomFilter
          .putIfAbsent(meta.bloomFilterValue, new AtomicLong())
          .foreach(_.incrementAndGet())
      }
    }
  }

  def remove(a: A): Unit = {
    synchronized {
      a.indexProperties.foreach { meta ⇒
        bloomFilter
          .get(meta.bloomFilterValue)
          .foreach { counter ⇒
            if (counter.decrementAndGet() <= 0) {
              bloomFilter.remove(meta.bloomFilterValue)
            }
          }
      }
    }
    a.indexProperties.foreach { key ⇒
      index.remove(key, a)
    }
    index.remove(All, a)
  }

  def clear(): Unit = {
    synchronized {
      index.clear()
      bloomFilter.clear()
    }
  }

  private def candidates[B](b: B)(implicit evidence: CanFuzzyMatchable[B]): Seq[A] = {
    evidence
      .indexProperties(bloomFilter, b)
      .map(index.getOrElse(_, Vector.empty[A]))
      .sortBy(_.size)
      .:+(index.getOrElse(All, Vector.empty[A]))
      .flatten
  }

  def lookupAll[B](b: B)(implicit evidence: CanFuzzyMatchable[B]): Seq[A] = {
    candidates(b).distinct.filter(_.matches(b))
  }

  private object All
}
