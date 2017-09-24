package com.hypertino.hyperbus.config

import com.hypertino.hyperbus.transport.api.{ClientTransport, ServerTransport, TransportRoute}

case class HyperbusConfiguration(clientRoutes: Seq[TransportRoute[ClientTransport]],
                                 serverRoutes: Seq[TransportRoute[ServerTransport]],
                                 registratorName: Option[String],
                                 schedulerName: Option[String],
                                 defaultGroupName: Option[String],
                                 readMessagesLogLevel: String,
                                 writeMessagesLogLevel: String
                                )
