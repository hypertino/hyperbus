package eu.inn.hyperbus.transport.api

import com.typesafe.config.{Config, ConfigFactory}
import eu.inn.hyperbus.util.ConfigUtils

class TransportConfigurationError(message: String) extends RuntimeException(message)

object TransportConfigurationLoader {
  import ConfigUtils._

  import scala.collection.JavaConversions._

  def fromConfig(config: Config): TransportConfiguration = {
    val sc = config.getConfig("service-bus")

    val st = sc.getObject("transports")
    val transportMap = st.entrySet().map { entry ⇒
      val transportTag = entry.getKey
      val transportConfig = sc.getConfig("transports."+transportTag)
      val transport = createTransport(transportConfig)
      transportTag → transport
    }.toMap

    import eu.inn.binders.tconfig._

    TransportConfiguration(
      sc.getList("client-routes").map{ li⇒
        val transportName = li.read[TransportNameHolder].transport
        getTransportRoute[ClientTransport](transportName, transportMap, li.read[TransportRouteHolder])
      }.toSeq,
      sc.getList("server-routes").map{ li⇒
        val transportName = li.read[TransportNameHolder].transport
        getTransportRoute[ServerTransport](transportName, transportMap, li.read[TransportRouteHolder])
      }.toSeq
    )
  }

  private def getTransportRoute[T](transportName: String, transportMap: Map[String, Any], config: TransportRouteHolder): TransportRoute[T] = {
    val transport = transportMap.getOrElse(transportName,
      throw new TransportConfigurationError(s"Couldn't find transport '$transportName'")
    ).asInstanceOf[T]

    val urlArg = getFilter(config.url, config.matchType)
    TransportRoute[T](transport, urlArg, config.partitionArgsN)
  }

  private def createTransport(config: Config): Any = {
    val className = {
      val s = config.getString("class-name")
      if (s.contains("."))
        s
      else
        "eu.inn.hyperbus.transport." + s
    }
    val clazz = Class.forName(className)
    val transportConfig = config.getOptionConfig("configuration").getOrElse(
      ConfigFactory.parseString("")
    )
    clazz.getConstructor(classOf[Config]).newInstance(transportConfig)
  }

  def getFilter(value: Option[String], matchType: Option[String]) = matchType match {
    case Some("Any") ⇒ AnyValue
    case Some("Regex") ⇒ RegexFilter(value.getOrElse(throw new TransportConfigurationError("Please provide value for Regex partition argument")))
    case _ ⇒ SpecificValue(value.getOrElse(throw new TransportConfigurationError("Please provide value for Exact partition argument")))
  }
}

case class TransportNameHolder(transport: String) // todo: separate transport name and route!

// todo: do we really need this? maybe it's just a Topic!
case class TransportRouteHolder(
                                 url: Option[String],
                                 matchType: Option[String],
                                 partitionArgs: Map[String, PartitionArgHolder] = Map.empty[String, PartitionArgHolder]) {// todo: rename partitionArgs
  def partitionArgsN: Filters = {
    Filters(
      partitionArgs.map { case (partitionKey, partitionValue) ⇒
        partitionKey → TransportConfigurationLoader.getFilter(partitionValue.value, partitionValue.matchType)
      }
    )
  }
}
case class PartitionArgHolder(value: Option[String], matchType: Option[String])