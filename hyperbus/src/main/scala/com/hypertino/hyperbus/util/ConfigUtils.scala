/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.util

import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigObject}

object ConfigUtils {
  import scala.collection.JavaConverters._

  implicit class ExtendConfig(val config: Config) extends AnyVal {
    def getOptionString(key: String): Option[String] =
      if (config.hasPath(key)) Some(config.getString(key)) else None

    def getString(key: String, default: String): String =
      if (config.hasPath(key)) config.getString(key) else default

    def getOptionConfig(key: String): Option[Config] =
      if (config.hasPath(key)) Some(config.getConfig(key)) else None

    def getOptionObject(key: String): Option[ConfigObject] =
      if (config.hasPath(key)) Some(config.getObject(key)) else None

    def getOptionList(key: String): Option[Seq[Config]] =
      if (config.hasPath(key)) Some(config.getConfigList(key).asScala.toSeq) else None

    def getOptionBoolean(key: String): Option[Boolean] =
      if (config.hasPath(key)) Some(config.getBoolean(key)) else None

    def getOptionLong(key: String): Option[Long] =
      if (config.hasPath(key)) Some(config.getLong(key)) else None

    def getOptionInt(key: String): Option[Int] =
      if (config.hasPath(key)) Some(config.getInt(key)) else None

    def getOptionDuration(key: String): Option[scala.concurrent.duration.FiniteDuration] =
      if (config.hasPath(key)) Some(
        scala.concurrent.duration.Duration(config.getDuration(key, TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
      )
      else None
  }

}
