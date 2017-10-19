package com.hypertino.hyperbus.config

import com.hypertino.hyperbus.transport.api._

case class HyperbusConfiguration(clientRoutes: Seq[ClientTransportRoute],
                                 serverRoutes: Seq[ServerTransportRoute],
                                 schedulerName: Option[String],
                                 defaultGroupName: Option[String],
                                 readMessagesLogLevel: String,
                                 writeMessagesLogLevel: String
                                )
