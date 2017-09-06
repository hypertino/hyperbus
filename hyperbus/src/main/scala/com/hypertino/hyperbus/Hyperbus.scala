package com.hypertino.hyperbus

import com.hypertino.hyperbus.config.{HyperbusConfiguration, HyperbusConfigurationLoader}
import com.hypertino.hyperbus.model.{MessageBase, RequestBase, RequestMeta, RequestObservableMeta}
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.util.SchedulerInjector
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import scaldi.{Injectable, Injector}

import scala.concurrent.duration.FiniteDuration

class Hyperbus(val defaultGroupName: Option[String],
               val messagesLogLevel: String,
               protected[this] val clientRoutes: Seq[TransportRoute[ClientTransport]],
               protected[this] val serverRoutes: Seq[TransportRoute[ServerTransport]],
               protected[this] implicit val scheduler: Scheduler,
               protected[this] val inj: Injector)
  extends HyperbusApi with Injectable with StrictLogging {

  def this(configuration: HyperbusConfiguration)(implicit inj: Injector) = this(
    configuration.defaultGroupName,
    configuration.messagesLogLevel.toUpperCase,
    configuration.clientRoutes,
    configuration.serverRoutes,
    SchedulerInjector(configuration.schedulerName),
    inj
  )

  def this(config: Config)(implicit inj: Injector) = this(HyperbusConfigurationLoader.fromConfig(config, inj))(inj)

  def shutdown(duration: FiniteDuration): Task[Boolean] = {
    Task.zipMap2(
      Task.gatherUnordered(clientRoutes.map(_.transport.shutdown(duration))),
      Task.gatherUnordered(serverRoutes.map(_.transport.shutdown(duration)))
    ) { (c, s) ⇒
      s.forall(_ == true) && c.forall(_ == true)
    }
  }

  override def ask[REQ <: RequestBase, M <: RequestMeta[REQ]](request: REQ)(implicit requestMeta: M): Task[M#ResponseType] = {
    logMessage(request, isClient = true, isEvent = false)
    this
      .lookupClientTransport(request)
      .ask(request, requestMeta.responseDeserializer)
      .flatMap {
        case e: Throwable ⇒
          logMessage(e, isClient = true, isEvent = false)
          Task.raiseError(e)

        case other ⇒
          logMessage(other, isClient = true, isEvent = false)
          Task.now(other.asInstanceOf[M#ResponseType])
      }
  }

  override def publish[REQ <: RequestBase](request: REQ)(implicit requestMeta: RequestMeta[REQ]): Task[PublishResult] = {
    logMessage(request, isClient = true, isEvent = true)
    lookupClientTransport(request).publish(request)
  }

  // todo: implement logging of server-side commands
  override def commands[REQ <: RequestBase](implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[CommandEvent[REQ]] = {
    lookupServerTransport(observableMeta.requestMatcher)
      .commands(observableMeta.requestMatcher, requestMeta.apply)
  }

  // todo: implement logging of server-side events
  override def events[REQ <: RequestBase](groupName: Option[String])(implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[REQ] = {
    val finalGroupName = groupName.getOrElse {
      defaultGroupName.getOrElse {
        throw new UnsupportedOperationException(s"Can't subscribe: group name is not defined")
      }
    }
    lookupServerTransport(observableMeta.requestMatcher)
      .events(observableMeta.requestMatcher, finalGroupName, requestMeta.apply)
  }

  protected def lookupClientTransport(message: RequestBase): ClientTransport = {
    clientRoutes.find { route ⇒
      route.matcher.matchMessage(message)
    } map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"Message headers: ${message.headers}")
    }
  }

  protected def lookupServerTransport(requestMatcher: RequestMatcher): ServerTransport = {
    serverRoutes.find { route ⇒
      route.matcher.matchRequestMatcher(requestMatcher)
    } map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"${requestMatcher.headers}")
    }
  }

  protected def isLoggingMessages: Boolean = messagesLogLevel match {
    case "TRACE" ⇒ logger.underlying.isTraceEnabled()
    case "DEBUG" ⇒ logger.underlying.isDebugEnabled()
    case "INFO" ⇒ logger.underlying.isInfoEnabled()
    case "WARN" ⇒ logger.underlying.isWarnEnabled()
    case "ERROR" ⇒ logger.underlying.isErrorEnabled()
  }

  protected def logMessage(message: MessageBase, isClient: Boolean, isEvent: Boolean): Unit = {
    if (isLoggingMessages) {
      val direction = (message.isInstanceOf[RequestBase], isEvent) match {
        case (true, false) ⇒ "-~>"
        case (true, true) ⇒ "-|>"
        case (false, false) ⇒ "<~s-"
        case (false, true) ⇒ "???"
      }
      val hfrom = if (isClient) "" else "(h)"
      val hto = if (isClient) "(h)" else ""
      val msg = s"$hfrom$direction$hto: $message"

      messagesLogLevel match {
        case "TRACE" ⇒ logger.trace(msg)
        case "DEBUG" ⇒ logger.debug(msg)
        case "INFO" ⇒ logger.info(msg)
        case "WARN" ⇒ logger.warn(msg)
        case "ERROR" ⇒ logger.error(msg)
      }
    }
  }
}
