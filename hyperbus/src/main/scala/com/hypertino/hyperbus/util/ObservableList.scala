package com.hypertino.hyperbus.util

import com.typesafe.scalalogging.StrictLogging
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.ConcurrentSubject

import scala.concurrent.Future

class ObservableList[T](underlying: Seq[Observable[T]])(implicit val scheduler: Scheduler) extends Observable[T] with StrictLogging {
  private val subject = ConcurrentSubject.publishToOne[T]
  private val completionLock = new Object
  private var proxies: Seq[Proxy] = Seq.empty

  override def unsafeSubscribeFn(subscriber: Subscriber[T]): Cancelable = {
    val original: Cancelable = subject.unsafeSubscribeFn(subscriber)
    proxies = underlying.map(new Proxy(_))
    new Cancelable {
      override def cancel(): Unit = {
        original.cancel()
        completionLock.synchronized {
          proxies.foreach { proxy ⇒
            if (!proxy.isComplete) {
              proxy.cancelable.cancel()
            }
          }
        }
      }
    }
  }

  private class Proxy(observable: Observable[T]) extends Subscriber[T] {
    val cancelable = observable.subscribe(this)
    @volatile var isComplete = false
    override implicit def scheduler: Scheduler = ObservableList.this.scheduler
    override def onNext(elem: T): Future[Ack] = subject.onNext(elem)
    override def onError(ex: Throwable): Unit = {
      isComplete = true
      completionLock.synchronized {
        if (proxies.exists(!_.isComplete)) {
          logger.error("Error from observable is ignored", ex)
        }
        else {
          subject.onError(ex)
        }
      }
    }
    override def onComplete(): Unit = {
      isComplete = true
      completionLock.synchronized {
        if (proxies.forall(_.isComplete)) {
          subject.onComplete()
        }
      }
    }
  }
}

object ObservableList {
  def apply[T](sourceObservables: Seq[Observable[T]])(implicit scheduler: Scheduler): ObservableList[T] = {
    new ObservableList[T](sourceObservables)(scheduler)
  }
}
