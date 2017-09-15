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
