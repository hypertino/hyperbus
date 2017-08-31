package com.hypertino.hyperbus.subscribe

trait Subscribable {
  def groupName(existing: Option[String]): Option[String] = existing
}
