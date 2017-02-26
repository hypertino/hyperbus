package com.hypertino.hyperbus.util

import scala.collection.concurrent.TrieMap

trait CanComplexElement[T] {
  def upsert(existing: T, upsert: T): T
  def remove[A](existing: T, remove: A): T
  def isEmpty(existing: T): Boolean
}

// todo: this is too complicated, refactoring or documentation is needed
class ComplexTrieMap[K, V] {
  protected val map = new TrieMap[K, V]

  def get(key: K): Option[V] = map.get(key)

  def getOrElse(key: K, default: => V): V = map.getOrElse(key, default)

  def upsert(key: K, value: V)(implicit evidence: CanComplexElement[V]): Unit = {
    this.synchronized {
      map.putIfAbsent(key, value).map { existing =>
        val n = evidence.upsert(existing, value)
        val x = map.put(key, n)
        x
      }
    }
  }

  def remove[A](key: K, value: A)(implicit evidence: CanComplexElement[V]): Unit = {
    this.synchronized {
      map.get(key).map { existing =>
        val nv = evidence.remove(existing, value)
        if (evidence.isEmpty(nv))
          map.remove(key)
        else
          map.put(key, nv)
      }
    }
  }

  def foreach(code: ((K, V)) ⇒ Unit): Unit = map.foreach(code)

  def map[O](code: ((K, V)) ⇒ O): Iterable[O] = map.map(code)

  def clear(): Unit = map.clear()
}
