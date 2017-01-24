package com.hypertino.hyperbus.transport.api

import com.typesafe.config.{Config, ConfigFactory}
import com.hypertino.hyperbus.model.{Body, Request}
import com.hypertino.hyperbus.transport.api.matchers.{Any, RequestMatcher}
import com.hypertino.hyperbus.transport.api.uri.UriPattern$
import com.hypertino.hyperbus.util.ConfigUtils

class TransportConfigurationError(message: String) extends RuntimeException(message)

object TransportConfigurationLoader {

  import ConfigUtils._

  import scala.collection.JavaConversions._

  def fromConfig(config: Config): TransportConfiguration = {
    val sc = config.getConfig("hyperbus")

    val st = sc.getObject("transports")
    val transportMap = st.entrySet().map { entry ⇒
      val transportTag = entry.getKey
      val transportConfig = sc.getConfig("transports." + transportTag)
      val transport = createTransport(transportConfig)
      transportTag → transport
    }.toMap

    import com.hypertino.binders.config.ConfigBinders._

    TransportConfiguration(
      sc.getConfigList("client-routes").map { li ⇒
        val transportName = li.read[String]("transport")
        getTransportRoute[ClientTransport](transportName, transportMap, li)
      },
      sc.getConfigList("server-routes").map { li ⇒
        val transportName = li.read[String]("transport")
        getTransportRoute[ServerTransport](transportName, transportMap, li)
      }
    )
  }

  private def getTransportRoute[T](transportName: String, transportMap: Map[String, Any], config: Config): TransportRoute[T] = {
    val transport = transportMap.getOrElse(transportName,
      throw new TransportConfigurationError(s"Couldn't find transport '$transportName'")
    ).asInstanceOf[T]

    val matcher = if (config.hasPath("match"))
      RequestMatcher(config.getValue("match"))
    else
      RequestMatcher.any
    TransportRoute[T](transport, matcher)
  }

  private def createTransport(config: Config): Any = {
    val className = {
      val s = config.getString("class-name")
      if (s.contains("."))
        s
      else
        "com.hypertino.hyperbus.transport." + s
    }
    val clazz = Class.forName(className)
    val transportConfig = config.getOptionConfig("configuration").getOrElse(
      ConfigFactory.parseString("")
    )
    clazz.getConstructor(classOf[Config]).newInstance(transportConfig)
  }
}
