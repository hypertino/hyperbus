package com.hypertino.hyperbus

import com.hypertino.hyperbus.config.{HyperbusConfiguration, HyperbusConfigurationLoader}
import com.hypertino.hyperbus.model.{RequestBase, RequestMeta, RequestObservableMeta}
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.util.SchedulerInjector
import com.typesafe.config.Config
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.concurrent.duration.FiniteDuration

class Hyperbus(val defaultGroupName: Option[String],
               val logMessages: Boolean,
               protected[this] val clientRoutes: Seq[TransportRoute[ClientTransport]],
               protected[this] val serverRoutes: Seq[TransportRoute[ServerTransport]],
               protected[this] implicit val scheduler: Scheduler,
               protected[this] val inj: Injector)
  extends HyperbusApi with Injectable {

  def this(configuration: HyperbusConfiguration)(implicit inj: Injector) = this(
    configuration.defaultGroupName,
    configuration.logMessages,
    configuration.clientRoutes,
    configuration.serverRoutes,
    SchedulerInjector(configuration.schedulerName),
    inj
  )

  def this(config: Config)(implicit inj: Injector) = this(HyperbusConfigurationLoader.fromConfig(config, inj))(inj)

  protected val log = LoggerFactory.getLogger(this.getClass)

  def shutdown(duration: FiniteDuration): Task[Boolean] = {
    Task.zipMap2(
      Task.gatherUnordered(clientRoutes.map(_.transport.shutdown(duration))),
      Task.gatherUnordered(serverRoutes.map(_.transport.shutdown(duration)))
    ) { (c, s) ⇒
      s.forall(_ == true) && c.forall(_ == true)
    }
  }

  override def ask[REQ <: RequestBase, M <: RequestMeta[REQ]](request: REQ)(implicit requestMeta: M): Task[M#ResponseType] = {
    this
      .lookupClientTransport(request)
      .ask(request, requestMeta.responseDeserializer)
      .flatMap {
        case e: Throwable ⇒ Task.raiseError(e)
        case other ⇒ Task.now(other.asInstanceOf[M#ResponseType])
      }
  }

  override def publish[REQ <: RequestBase](request: REQ)(implicit requestMeta: RequestMeta[REQ]): Task[PublishResult] = {
    lookupClientTransport(request).publish(request)
  }

  override def commands[REQ <: RequestBase](implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[CommandEvent[REQ]] = {
    lookupServerTransport(observableMeta.requestMatcher).commands(observableMeta.requestMatcher, requestMeta.apply)
  }

  override def events[REQ <: RequestBase](groupName: Option[String])(implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[REQ] = {
    val finalGroupName = groupName.getOrElse {
      defaultGroupName.getOrElse {
        throw new UnsupportedOperationException(s"Can't subscribe: group name is not defined")
      }
    }
    lookupServerTransport(observableMeta.requestMatcher).events(observableMeta.requestMatcher, finalGroupName, requestMeta.apply)
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
}
