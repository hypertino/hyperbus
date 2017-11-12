/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.config

import com.hypertino.hyperbus.transport.api._

case class HyperbusConfiguration(clientRoutes: Seq[ClientTransportRoute],
                                 serverRoutes: Seq[ServerTransportRoute],
                                 schedulerName: Option[String],
                                 defaultGroupName: Option[String],
                                 readMessagesLogLevel: String,
                                 writeMessagesLogLevel: String,
                                 serverReadMessagesLogLevel: String,
                                 serverWriteMessagesLogLevel: String
                                )
