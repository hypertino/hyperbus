package com.hypertino.hyperbus.util

import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.eval.Task
import monix.execution.Ack.Stop
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.ConcurrentSubject

import scala.util.Success

abstract class HyperbusSubscription[T](implicit val scheduler: Scheduler) extends FuzzyMatcher {
  // FyzzyIndex properties
  def requestMatcher: RequestMatcher
  override def indexProperties: Seq[FuzzyIndexItemMetaInfo] = requestMatcher.indexProperties
  override def matches(other: Any): Boolean = requestMatcher.matches(other)

  // Subject properties
  protected val subject: ConcurrentSubject[T,T] = ConcurrentSubject.publishToOne[T]
  def off(): Unit = {
    remove()
    subject.onComplete()
  }

  def publish(t: T): Task[Ack] = {
    Task.fromFuture(subject.onNext(t).andThen {
      case Success(Stop) ⇒ remove()
    })
  }

  val observable: Observable[T] = (subscriber: Subscriber[T]) => {
    val original: Cancelable = subject.unsafeSubscribeFn(subscriber)
    add()
    () => {
      off()
      original.cancel()
    }
  }

  protected def remove(): Unit
  protected def add(): Unit
}
