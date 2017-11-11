package com.hypertino.hyperbus

import com.hypertino.hyperbus.config.{HyperbusConfiguration, HyperbusConfigurationLoader}
import com.hypertino.hyperbus.model.{Header, HyperbusError, MessageBase, Method, RequestBase, RequestMeta, RequestObservableMeta, ResponseBase}
import com.hypertino.hyperbus.transport.api.{NoTransportRouteException, _}
import com.hypertino.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import com.hypertino.hyperbus.util.SchedulerInjector
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.eval.{Callback, Task}
import monix.execution.{Cancelable, Scheduler}
import monix.reactive.Observable
import scaldi.{Injectable, Injector}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class Hyperbus(val defaultGroupName: Option[String],
               protected[this] val readMessagesLogLevel: String,
               protected[this] val writeMessagesLogLevel: String,
               protected[this] val serverReadMessagesLogLevel: String,
               protected[this] val serverWriteMessagesLogLevel: String,
               protected[this] val clientRoutes: Seq[ClientTransportRoute],
               protected[this] val serverRoutes: Seq[ServerTransportRoute],
               protected[this] implicit val scheduler: Scheduler,
               protected[this] val inj: Injector)
  extends HyperbusApi with Injectable with StrictLogging {

  def this(configuration: HyperbusConfiguration)(implicit inj: Injector) = this(
    configuration.defaultGroupName,
    configuration.readMessagesLogLevel.toUpperCase,
    configuration.writeMessagesLogLevel.toUpperCase,
    configuration.serverReadMessagesLogLevel.toUpperCase,
    configuration.serverWriteMessagesLogLevel.toUpperCase,
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
    logMessage(request, request, isClient = true, isEvent = false)
    val routes = lookupClientTransports(request)
    askFirst(request, routes.map(_.transport))
      .materialize
      .map {
        case Success(e: Throwable) ⇒
          logMessage(request, e, isClient = true, isEvent = false)
          Failure(e)

        case Success(other) ⇒
          logMessage(request, other, isClient = true, isEvent = false)
          Success(other.asInstanceOf[M#ResponseType])

        case Failure(e: HyperbusError[_]) ⇒
          logMessage(request, e, isClient = true, isEvent = false)
          Failure(e)

        case Failure(e: Throwable) ⇒
          logThrowableResponse(request, e, isClient = true)
          Failure(e)
      }
      .dematerialize
  }

  private def askFirst[REQ <: RequestBase, M <: RequestMeta[REQ]](request: REQ, transports: Seq[ClientTransport])(implicit requestMeta: M): Task[M#ResponseType] = {
    transports
      .headOption
      .map { transport ⇒
        transport
          .ask(request, requestMeta.responseDeserializer)
          .materialize
          .flatMap {
            case Failure(e: NoTransportRouteException) ⇒
              askFirst(request, transports.tail)
            case Success(r) ⇒ Task.now(r.asInstanceOf[M#ResponseType])
            case Failure(e) ⇒ Task.raiseError(e)
          }
      }
      .getOrElse {
        Task.raiseError(new NoTransportRouteException(s"Message headers: ${request.headers}"))
      }
  }

  override def publish[REQ <: RequestBase](request: REQ)(implicit requestMeta: RequestMeta[REQ]): Task[Seq[Any]] = {
    val routes = lookupClientTransports(request)
    if (routes.isEmpty) {
      logMessage(request, request, isClient = true, isEvent = true, s = "IGNORED: ", forceLevel = Some("WARN"))
      Task.now(Seq.empty)
    }
    else {
      logMessage(request, request, isClient = true, isEvent = true)
      Task.gather(routes
        .map(_.transport.publish(request))
      )
    }
  }

  override def commands[REQ <: RequestBase](implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[CommandEvent[REQ]] = {
    val routes = lookupServerTransports(observableMeta.requestMatcher)
    val (observable, registrators) = if (routes.isEmpty) {
      throw new NoTransportRouteException(s"Matcher headers: ${observableMeta.requestMatcher.headers}")
    } else if (routes.tail.isEmpty) {
      val route = routes.head
      (route.transport.commands(observableMeta.requestMatcher, requestMeta.apply), Seq(route.registrator))
    } else {
      (Observable.mergeDelayError(routes.map(_.transport.commands(observableMeta.requestMatcher, requestMeta.apply)): _*),
        routes.map(_.registrator))
    }

    val loggingObservable = if (isLoggingMessages(readMessagesLogLevel) || isLoggingMessages(writeMessagesLogLevel)) {
      observable
        .map { command ⇒
          logMessage(command.request, command.request, isClient = false, isEvent = false)
          val loggingCommand = command.copy(
            reply = new Callback[ResponseBase] {
              override def onSuccess(value: ResponseBase): Unit = {
                logMessage(command.request, value, isClient = false, isEvent = false)
                command.reply.onSuccess(value)
              }

              override def onError(ex: Throwable): Unit = {
                logThrowableResponse(command.request, ex, isClient = false)
                command.reply.onError(ex)
              }
            }
          )
          loggingCommand
        }
    } else {
      observable
    }

    wrapObservable(loggingObservable, observableMeta.requestMatcher, registrators)
  }

  private def wrapObservable[T](o: Observable[T], requestMatcher: RequestMatcher, registrators: Seq[ServiceRegistrator]): Observable[T] = {
    val to = requestMatcher.headers.toString
    val registrations = Task.gatherUnordered {
      registrators.map { r ⇒
        r.registerService(requestMatcher)
          .onErrorRecover {
            case t: Throwable ⇒
              logger.error(s"Registration of subscription $o to $to is failed", t)
              Cancelable.empty
          }
      }
    }.memoize

    o.doAfterSubscribe(() ⇒ {
      logger.info(s"Subscription #$o to $to is started")
      registrations.runAsync
    })
      .doOnSubscriptionCancel(() ⇒ {
        logger.info(s"Subscription #$o to $to is canceled")
        registrations.map(_.foreach(_.cancel)).runAsync
      })
      .doOnTerminateEval {
        case Some(ex) ⇒
          logger.info(s"Subscription #$o to $to is failed", ex)
          registrations.map(_.foreach(_.cancel))
        case None ⇒
          logger.info(s"Subscription #$o to $to is complete")
          registrations.map(_.foreach(_.cancel))
      }
  }

  override def events[REQ <: RequestBase](groupName: Option[String])(implicit requestMeta: RequestMeta[REQ], observableMeta: RequestObservableMeta[REQ]): Observable[REQ] = {
    val finalGroupName = groupName.getOrElse {
      defaultGroupName.getOrElse {
        throw new UnsupportedOperationException(s"Can't subscribe: group name is not defined")
      }
    }
    val routes = lookupServerTransports(observableMeta.requestMatcher)
    val (observable, registrators) = if (routes.isEmpty) {
      throw new NoTransportRouteException(s"Matcher headers: ${observableMeta.requestMatcher.headers}")
    } else if (routes.tail.isEmpty) {
      val route = routes.head
      (route.transport.events(observableMeta.requestMatcher, finalGroupName, requestMeta.apply), Seq(route.registrator))
    } else {
      (Observable.mergeDelayError(routes.map(_.transport.events(observableMeta.requestMatcher, finalGroupName, requestMeta.apply)):_*),
        routes.map(_.registrator))
    }

    val loggingObservable = if (isLoggingMessages(readMessagesLogLevel) || isLoggingMessages(writeMessagesLogLevel)) {
      observable
        .doOnNext { event ⇒
          logMessage(event, event, isClient = false, isEvent = true)
        }
    }else {
      observable
    }

    wrapObservable(loggingObservable, observableMeta.requestMatcher, registrators)
  }

  protected def lookupClientTransports(message: RequestBase): Seq[ClientTransportRoute] = {
    clientRoutes.filter(_.matcher.matchMessage(message))
  }

  protected def lookupServerTransports(requestMatcher: RequestMatcher): Seq[ServerTransportRoute] = {
    serverRoutes.filter(_.matcher.matchRequestMatcher(requestMatcher))
  }

  protected def isLoggingMessages(level: String): Boolean = level match {
    case "TRACE" ⇒ logger.underlying.isTraceEnabled()
    case "DEBUG" ⇒ logger.underlying.isDebugEnabled()
    case "INFO" ⇒ logger.underlying.isInfoEnabled()
    case "WARN" ⇒ logger.underlying.isWarnEnabled()
    case "ERROR" ⇒ logger.underlying.isErrorEnabled()
    case _ ⇒ false
  }

  protected def logMessage(request: RequestBase, message: MessageBase, isClient: Boolean, isEvent: Boolean,
                           s: String = "",
                           forceLevel: Option[String] = None): Unit = {
    val level = forceLevel.getOrElse {
      request.headers.method match {
        case Method.GET ⇒ readMessagesLogLevel
        case _ ⇒ writeMessagesLogLevel
      }
    }

    if (isLoggingMessages(level)) {
      val direction = (message.isInstanceOf[RequestBase], isEvent) match {
        case (true, false) ⇒ "-~>"
        case (true, true) ⇒ "-|>"
        case (false, false) ⇒ "<~s-"
        case (false, true) ⇒ "???"
      }
      val hfrom = if (isClient) "" else "(h)"
      val hto = if (isClient) "(h)" else ""
      val msg = s"$s$hfrom$direction$hto: $message"

      logWithLevel(msg, level)
    }
  }

  protected def logThrowableResponse(request: RequestBase, e: Throwable, isClient: Boolean): Unit = {
    val level = request.headers.method match {
      case Method.GET ⇒ readMessagesLogLevel
      case _ ⇒ writeMessagesLogLevel
    }

    if (isLoggingMessages(level)) {
      val direction = "<~s-"
      val hfrom = if (isClient) "" else "(h)"
      val hto = if (isClient) "(h)" else ""
      val msg = s"$hfrom$direction$hto: $e"

      logWithLevel(msg, level)
    }
  }

  protected def logWithLevel(s: String, level: String): Unit = {
    level match {
      case "TRACE" ⇒ logger.trace(s)
      case "DEBUG" ⇒ logger.debug(s)
      case "INFO" ⇒ logger.info(s)
      case "WARN" ⇒ logger.warn(s)
      case "ERROR" ⇒ logger.error(s)
      case _ ⇒
    }
  }
}
