package com.hypertino.hyperbus

import com.hypertino.hyperbus.model.{RequestBase, RequestMeta}
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
               val logMessages: Boolean = true)(implicit val scheduler: Scheduler)
  extends HyperbusApi {

  protected val log = LoggerFactory.getLogger(this.getClass)

    def shutdown(duration: FiniteDuration): Task[Boolean] = {
    transportManager.shutdown(duration)
  }

  override def <~[REQ <: RequestBase](request: REQ)(implicit requestMeta: RequestMeta[REQ]): Task[requestMeta.ResponseType] = {
    transportManager.ask(request, requestMeta.responseDeserializer).asInstanceOf[Task[requestMeta.ResponseType]]
  }

  override def <|[REQ <: RequestBase](request: REQ)(implicit requestMeta: RequestMeta[REQ]): Task[PublishResult] = {
    transportManager.publish(request)
  }

  override def commands[REQ <: RequestBase](requestMatcher: RequestMatcher)(implicit requestMeta: RequestMeta[REQ]): Observable[CommandEvent[REQ]] = {
    transportManager.commands(requestMatcher, requestMeta.apply)
  }

  override def events[REQ <: RequestBase](requestMatcher: RequestMatcher, groupName: Option[String])(implicit requestMeta: RequestMeta[REQ]): Observable[REQ] = {
    val finalGroupName = groupName.getOrElse {
      defaultGroupName.getOrElse {
        throw new UnsupportedOperationException(s"Can't subscribe: group name is not defined")
      }
    }
    transportManager.events(requestMatcher, finalGroupName, requestMeta.apply)
  }
}
