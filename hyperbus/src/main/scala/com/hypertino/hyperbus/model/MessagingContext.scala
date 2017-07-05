package com.hypertino.hyperbus.model

import com.hypertino.hyperbus.util.SeqGenerator

trait MessagingContext {
  def createMessageId(): String = SeqGenerator.create()
  def correlationId: String
}

object MessagingContext {
  val empty = new MessagingContext {
    override def correlationId: String = throw new RuntimeException("No correlationId for empty context")
    override def toString = s"MessagingContext.empty"
  }

  object Implicits {
    implicit val emptyContext: MessagingContext = MessagingContext.empty
  }

  def apply(withCorrelationId: String): MessagingContext = new MessagingContext {
    def correlationId: String = withCorrelationId

    override def toString = s"MessagingContext(correlationId=$correlationId)"
  }
}
