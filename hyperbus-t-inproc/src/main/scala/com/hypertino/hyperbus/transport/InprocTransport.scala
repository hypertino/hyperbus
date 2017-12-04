/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport

import java.io.StringReader

import com.hypertino.hyperbus.model.{Message, RequestBase, ResponseBase}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.util.ConfigUtils._
import com.hypertino.hyperbus.util._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.ConcurrentSubject
import scaldi.Injector

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

class InprocTransport(serialize: Boolean = false)
                     (implicit val scheduler: Scheduler) extends ClientTransport with ServerTransport {

  def this(config: Config, inj: Injector) = this(
    serialize = config.getOptionBoolean("serialize").getOrElse(false)
  )(
    SchedulerInjector(config.getOptionString("scheduler"))(inj)
  )

  protected val commandSubscriptions = new FuzzyIndex[CommandSubscription]
  protected val eventSubscriptions = new FuzzyIndex[EventSubscription]
  protected implicit val serializationOptions = SerializationOptions.forceOptionalFields

  // todo: refactor this method, it's awful
  protected def _ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer, isPublish: Boolean): Task[_] = {
    // todo: handle serialization exceptions

    val serialized = serialize(message)
    val resultTask: Option[Task[ResponseBase]] = getRandom(commandSubscriptions
      .lookupAll(message)).map { subscription ⇒

      val request: RequestBase = serialized.map { serializedMessage ⇒
        MessageReader.fromString(serializedMessage, subscription.inputDeserializer)
      } getOrElse {
        message
      }

      val callbackTask = CallbackTask[ResponseBase]
      val command = CommandEvent(request, callbackTask)
      val tPublish = subscription.publish(command)
      val t = Task.zip2(tPublish, callbackTask.task).map(_._2)

      if (serialize) {
        t.map { result ⇒
          MessageReader.fromString(result.serializeToString, responseDeserializer)
        } onErrorHandleWith {
          case r: ResponseBase ⇒
            Task.now(MessageReader.fromString(r.serializeToString, responseDeserializer))
        }
      }
      else {
        t
      }
    }

    if (!isPublish && resultTask.isEmpty) {
      Task.raiseError {
        new NoTransportRouteException(s"${message.headers.hrl.location} is not found. Headers: ${message.headers}")
      }
    }
    else {
      val publishTasks = eventSubscriptions
        .lookupAll(message)
        .groupBy(_.group)
        .map { subscriptions ⇒
          val subscription = getRandom(subscriptions._2).get

          val request: RequestBase = serialized.map { serializedMessage ⇒
            MessageReader.fromString(serializedMessage, subscription.inputDeserializer)
          } getOrElse {
            message
          }

          subscription.publish(request)
        }.toSeq ++ resultTask

      if (isPublish) {
        Task.zipList(publishTasks: _*).map { _ ⇒
          PublishResult.empty
        }
      } else {
        resultTask.get
      }
    }
  }

  override def ask(message: RequestBase, outputDeserializer: ResponseBaseDeserializer): Task[ResponseBase] = {
    _ask(message, outputDeserializer, isPublish = false).asInstanceOf[Task[ResponseBase]]
  }

  override def publish(message: RequestBase): Task[PublishResult] = {
    _ask(message, null, isPublish = true).asInstanceOf[Task[PublishResult]]
  }

  def commands[REQ <: RequestBase](matcher: RequestMatcher,
                                     inputDeserializer: RequestDeserializer[REQ]): Observable[CommandEvent[REQ]] = {

    new CommandSubscription(matcher, inputDeserializer)
      .observable
      .asInstanceOf[Observable[CommandEvent[REQ]]]
  }

  def events[REQ <: RequestBase](matcher: RequestMatcher,
                                   groupName: String,
                                   inputDeserializer: RequestDeserializer[REQ]): Observable[REQ] = {

    new EventSubscription(matcher, groupName, inputDeserializer)
      .observable
      .asInstanceOf[Observable[REQ]]
  }

  override def shutdown(duration: FiniteDuration): Task[Boolean] = {
    eventSubscriptions.toSeq.foreach(_.stop())
    commandSubscriptions.toSeq.foreach(_.stop())
    eventSubscriptions.clear()
    commandSubscriptions.clear()
    Task.now(true)
  }

  private def serialize(message: Message[_,_]): Option[String] = {
    if (serialize) {
      Some(message.serializeToString)
    }
    else {
      None
    }
  }

  private val random = new Random()
  def getRandom[T](seq: Seq[T]): Option[T] = {
    val size = seq.size
    if (size > 1)
      Some(seq(random.nextInt(size)))
    else
      seq.headOption
  }



  protected class CommandSubscription(val requestMatcher: RequestMatcher,
                                      val inputDeserializer: RequestDeserializer[RequestBase])
    extends SubjectSubscription[CommandEvent[RequestBase]] {

    override protected val subject = ConcurrentSubject.publishToOne[CommandEvent[RequestBase]]

    override def remove(): Unit = {
      commandSubscriptions.remove(this)
    }
    override def add(): Unit = {
      commandSubscriptions.add(this)
    }
  }

  protected class EventSubscription(val requestMatcher: RequestMatcher,
                                    val group: String,
                                    val inputDeserializer: RequestDeserializer[RequestBase])
    extends SubjectSubscription[RequestBase] {

    override protected val subject = ConcurrentSubject.publishToOne[RequestBase]

    override def remove(): Unit = {
      eventSubscriptions.remove(this)
    }
    override def add(): Unit = {
      eventSubscriptions.add(this)
    }
  }
}

