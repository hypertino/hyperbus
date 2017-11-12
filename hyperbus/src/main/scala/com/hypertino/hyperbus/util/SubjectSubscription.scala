/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

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
  def stop(): Unit = {
    remove()
    subject.onComplete()
  }

  def publish(t: eventType): Task[Ack] = {
    Task.fromFuture(subject.onNext(t).andThen {
      case Success(Stop) ⇒ remove()
    })
  }

  def observable: Observable[eventType] = {
    subject
      .doOnSubscriptionCancel(remove)
      .doOnSubscribe(add)
  }

  protected def remove(): Unit
  protected def add(): Unit
}
