package com.hypertino.hyperbus.transport.api

trait ServiceEndpoint {
  def hostname: String
  def port: Option[Int]
}
