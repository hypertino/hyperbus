package com.hypertino.hyperbus.util

import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.eval.Task
import monix.execution.Ack.Stop
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.{Observable, Observer}
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.{ConcurrentSubject, Subject}

import scala.util.Success

abstract class SubjectSubscription[T](implicit val scheduler: Scheduler) extends FuzzyMatcher {
  type eventType = T

  // FyzzyIndex properties
  def requestMatcher: RequestMatcher
  override def indexProperties: Seq[FuzzyIndexItemMetaInfo] = requestMatcher.indexProperties
  override def matches(other: Any): Boolean = requestMatcher.matches(other)

  // Subject properties
  protected val subject: Subject[eventType, eventType]
  def cancel(): Unit = {
    remove()
    subject.onComplete()
  }

  def publish(t: eventType): Task[Ack] = {
    Task.fromFuture(subject.onNext(t).andThen {
      case Success(Stop) ⇒ remove()
    })
  }

  private def cancel_1() = cancel()

  val observable: Observable[eventType] = new Observable[eventType] {
    override def unsafeSubscribeFn(subscriber: Subscriber[eventType]): Cancelable = {
      val original: Cancelable = subject.unsafeSubscribeFn(subscriber)
      add()
      new Cancelable {
        override def cancel(): Unit = {
          cancel_1()
          original.cancel()
        }
      }
    }
  }

  protected def remove(): Unit
  protected def add(): Unit
}
