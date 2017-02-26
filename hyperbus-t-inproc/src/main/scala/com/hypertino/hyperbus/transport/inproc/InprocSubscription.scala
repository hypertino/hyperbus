package com.hypertino.hyperbus.transport.inproc

import com.hypertino.hyperbus.model.{RequestBase, ResponseBase}
import com.hypertino.hyperbus.serialization.RequestDeserializer
import com.hypertino.hyperbus.transport.api.Subscription
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.util.{FuzzyIndexItemMetaInfo, FuzzyMatcher}
import rx.lang.scala.Observer

import scala.concurrent.Future

trait InprocSubscription extends FuzzyMatcher with Subscription {
  def requestMatcher: RequestMatcher
  override def indexProperties: Seq[FuzzyIndexItemMetaInfo] = requestMatcher.indexProperties
  override def matches(other: Any): Boolean = requestMatcher.matches(other)
}

case class InprocCommandSubscription(requestMatcher: RequestMatcher,
                                     inputDeserializer: RequestDeserializer[RequestBase],
                                     handler: RequestBase => Future[ResponseBase]) extends InprocSubscription

case class InprocEventSubscription(requestMatcher: RequestMatcher,
                              group: String,
                              inputDeserializer: RequestDeserializer[RequestBase],
                              handler: Observer[RequestBase]) extends InprocSubscription
