package com.hypertino.hyperbus.transport.inproc

import com.hypertino.hyperbus.model.RequestBase
import com.hypertino.hyperbus.serialization.RequestDeserializer
import com.hypertino.hyperbus.transport.api.CommandEvent
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.util.FuzzyMatcher
import monix.execution.Cancelable

//
//private[transport] abstract class InprocObservable[T] extends FuzzyMatcher {
//  final val MAX_BUFFER_SIZE = 1000
//  def requestMatcher: RequestMatcher
//}
//
//private[transport] case class InprocCommandHyperbusObservable(requestMatcher: RequestMatcher,
//                                                              inputDeserializer: RequestDeserializer[RequestBase],
//                                                              cancelable: Cancelable
//                                                             )
//  extends InprocObservable[CommandEvent[RequestBase]]
//
//private[transport] case class InprocEventHyperbusObservable(requestMatcher: RequestMatcher,
//                                                            group: String,
//                                                            inputDeserializer: RequestDeserializer[RequestBase],
//                                                            cancelable: Cancelable
//                                                           )
//  extends InprocObservable[RequestBase]
