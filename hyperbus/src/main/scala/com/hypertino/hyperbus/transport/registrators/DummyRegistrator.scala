/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.transport.registrators

import com.hypertino.hyperbus.transport.api.ServiceRegistrator
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.eval.Task
import monix.execution.Cancelable

object DummyRegistrator extends ServiceRegistrator{
  override def registerService(requestMatcher: RequestMatcher): Task[Cancelable] = Task.now(Cancelable.empty)
}
