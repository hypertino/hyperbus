package com.hypertino.hyperbus

import com.hypertino.hyperbus.model.{RequestBase, RequestMeta, RequestObservableMeta}
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.language.experimental.macros

class Hyperbus(val transportManager: TransportManager,
               val defaultGroupName: Option[String] = None,
               val logMessages: Boolean = true)
  extends HyperbusApi {

  protected val log = LoggerFactory.getLogger(this.getClass)

    def shutdown(duration: FiniteDuration): Task[Boolean] = {
    transportManager.shutdown(duration)
  }

  override def ask[REQ <: RequestBase](request: REQ)(implicit requestMeta: RequestMeta[REQ]): Task[requestMeta.ResponseType] = {
    transportManager.ask(request, requestMeta.responseDeserializer).asInstanceOf[Task[requestMeta.ResponseType]]
  }

  override def publish[REQ <: RequestBase](request: REQ)(implicit requestMeta: RequestMeta[REQ]): Task[PublishResult] = {
    transportManager.publish(request)
  }

  override def commands[REQ <: RequestBase](implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[CommandEvent[REQ]] = {
    transportManager.commands(observableMeta.requestMatcher, requestMeta.apply)
  }

  override def events[REQ <: RequestBase](groupName: Option[String])(implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[REQ] = {
    val finalGroupName = groupName.getOrElse {
      defaultGroupName.getOrElse {
        throw new UnsupportedOperationException(s"Can't subscribe: group name is not defined")
      }
    }
    transportManager.events(observableMeta.requestMatcher, finalGroupName, requestMeta.apply)
  }
}
