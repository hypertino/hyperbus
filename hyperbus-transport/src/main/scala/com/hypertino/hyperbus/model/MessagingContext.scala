package com.hypertino.hyperbus.model

import com.hypertino.hyperbus.util.SeqGenerator

trait MessagingContext {
  def createMessageId(): String = SeqGenerator.create()
  def correlationId: Option[String]
}

object MessagingContext {
  val empty = MessagingContext(None)

  object Implicits {
    implicit val empty: MessagingContext = MessagingContext.empty
  }

  def apply(withCorrelationId: Option[String]): MessagingContext = new MessagingContext {
    def correlationId: Option[String] = withCorrelationId

    override def toString = s"MessagingContext(correlationId=$correlationId)"
  }

  def apply(withCorrelationId: String): MessagingContext = apply(Some(withCorrelationId))
}
