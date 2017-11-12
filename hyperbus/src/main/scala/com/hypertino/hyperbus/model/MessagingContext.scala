/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model

import com.hypertino.hyperbus.util.SeqGenerator

trait MessagingContext {
  def createMessageId(): String = SeqGenerator.create()
  def correlationId: String
  def parentId: Option[String]
}

object MessagingContext {
  val empty = new MessagingContext {
    override def correlationId: String = throw new RuntimeException("No correlationId for empty context")
    override def toString = s"MessagingContext.empty"
    override def parentId: Option[String] = None
  }

  object Implicits {
    implicit val emptyContext: MessagingContext = MessagingContext.empty
  }

  def apply(withCorrelationId: String, withParentId: Option[String] = None): MessagingContext = new MessagingContext {
    def correlationId: String = withCorrelationId
    override def parentId: Option[String] = withParentId
    override def toString = s"MessagingContext(correlationId=$correlationId,parentId=$parentId)"
  }
}
