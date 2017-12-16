/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.util

import monix.eval.Task

import scala.concurrent.duration.FiniteDuration

class TaskUtils {
  def retryBackoff[A](source: Task[A],
                      maxRetries: Int, delay: FiniteDuration): Task[A] = {

    source.onErrorHandleWith {
      case ex: Exception =>
        if (maxRetries > 0)
        // Recursive call, it's OK as Monix is stack-safe
          retryBackoff(source, maxRetries-1, delay)
            .delayExecution(delay)
        else
          Task.raiseError(ex)
    }
  }
}
