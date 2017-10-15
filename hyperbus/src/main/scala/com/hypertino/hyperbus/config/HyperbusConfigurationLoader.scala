package com.hypertino.hyperbus.config

import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.util.{ConfigUtils, ServiceRegistratorInjector}
import com.typesafe.config.Config
import scaldi.Injector

class HyperbusConfigurationError(message: String) extends RuntimeException(message)

object HyperbusConfigurationLoader {

  import ConfigUtils._

  import scala.collection.JavaConversions._

  def fromConfig(config: Config, inj: Injector): HyperbusConfiguration = {
    val hc = config.getConfig("hyperbus")

    val st = hc.getObject("transports")
    val transportMap = st.entrySet().map { entry ⇒
      val transportTag = entry.getKey
      val transportConfig = hc.getConfig("transports." + transportTag)
      val transport = createTransport(transportConfig, inj)
      transportTag → transport
    }.toMap

    import com.hypertino.binders.config.ConfigBinders._

    HyperbusConfiguration(
      hc.getConfigList("client-routes").map { li ⇒
        val transportName = li.read[String]("transport")
        getClientTransportRoute(transportName, transportMap, li)
      },
      hc.getConfigList("server-routes").map { li ⇒
        val transportName = li.read[String]("transport")
        getServerTransportRoute(transportName, transportMap, li)(inj)
      },
      hc.getOptionString("registrator"),
      hc.getOptionString("scheduler"),
      hc.getOptionString("group-name"),
      hc.getOptionString("read-messages-log-level").getOrElse("TRACE"),
      hc.getOptionString("write-messages-log-level").getOrElse("DEBUG")
    )
  }

  private def getTransportAndMatcher[T](transportName: String, transportMap: Map[String, Any], config: Config): (T, RequestMatcher) = {
    val transport = transportMap.getOrElse(transportName,
      throw new HyperbusConfigurationError(s"Couldn't find transport '$transportName'")
    ).asInstanceOf[T]

    val matcher = if (config.hasPath("match"))
      RequestMatcher(config.getValue("match"))
    else
      RequestMatcher.any
    (transport, matcher)
  }

  private def getClientTransportRoute(transportName: String, transportMap: Map[String, Any], config: Config): ClientTransportRoute = {
    val (transport, matcher) = getTransportAndMatcher[ClientTransport](transportName,transportMap,config)
    ClientTransportRoute(transport,matcher)
  }

  private def getServerTransportRoute(transportName: String, transportMap: Map[String, Any], config: Config)(implicit inj: Injector): ServerTransportRoute = {
    val (transport, matcher) = getTransportAndMatcher[ServerTransport](transportName,transportMap,config)
    val registratorName = if (config.hasPath("registrator"))
      Some(config.getString("registrator"))
    else
      None
    ServerTransportRoute(transport,matcher,ServiceRegistratorInjector(registratorName)(inj))
  }

  private def createTransport(config: Config, inj: Injector): Any = {
    val className = {
      val s = config.getString("class-name")
      if (s.contains("."))
        s
      else
        "com.hypertino.hyperbus.transport." + s
    }
    val clazz = Class.forName(className)
//    val transportConfig = config.getOptionConfig("options").getOrElse(
//      ConfigFactory.parseString("")
//    )
    clazz.getConstructor(classOf[Config], classOf[Injector]).newInstance(config, inj)
  }
}
