package com.hypertino.hyperbus.impl

import java.io.Reader

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.{Body, Header, HeaderHRI, Response, ResponseHeaders}
import com.hypertino.hyperbus.serialization.ResponseBodyDeserializer
import com.hypertino.hyperbus.transport.api.matchers.{RequestMatcher, Specific}

trait MacroApi {
  def responseDeserializer(reader: Reader,
                           headersObj: Obj,
                           bodyDeserializer: PartialFunction[ResponseHeaders, ResponseBodyDeserializer]): Response[Body]

  def requestMatcher(serviceAddress: String, method: String, contentType: Option[String]) = RequestMatcher(
    serviceAddress, method, contentType
  )
}
