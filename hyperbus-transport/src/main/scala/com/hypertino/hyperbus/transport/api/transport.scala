package com.hypertino.hyperbus.transport.api

import java.io.{StringWriter, Writer}

import com.hypertino.binders.value.Value
import com.hypertino.hyperbus.model.{Body, Request}
import com.hypertino.hyperbus.serialization.RequestDeserializer
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.transport.api.uri.Uri
import rx.lang.scala.Observer

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait EntityWithHeaders {
  def headers: Headers

  def headerOption(name: String): Option[Value] = headers.get(name)

  def header(name: String): Value = headerOption(name).getOrElse(throw new NoSuchHeaderException(name))

  def messageId: String = header(Header.MESSAGE_ID).toString

  def correlationId: Option[String] = headerOption(Header.CORRELATION_ID).map(_.toString).orElse(Some(messageId))

  def contentType: Option[String] = headerOption(Header.CONTENT_TYPE).map(_.toString)
}

trait TransportMessage extends EntityWithHeaders {
  def messageId: String

  def serialize(writer: Writer)

  def serializeToString: String = {
    val writer = new StringWriter()
    serialize(writer)
    writer.toString
  }
}

trait TransportRequest extends TransportMessage {
  def uri: Uri
}

trait TransportResponse extends TransportMessage

trait PublishResult {
  def sent: Option[Boolean]

  def offset: Option[String]
}

trait ClientTransport {
  def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse]

  def publish(message: TransportRequest): Future[PublishResult]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

trait Subscription

case class EventStreamSubscription(observableSubscription: rx.lang.scala.Subscription, transportSubscription: Subscription) extends Subscription

trait ServerTransport {
  // todo: instead of ((Request[Body]) => Future[TransportResponse]) use class like Observer[-T] with contravariance
  def onCommand[REQ <: Request[Body]](matcher: RequestMatcher,
                inputDeserializer: RequestDeserializer[REQ])
               (handler: (REQ) => Future[TransportResponse]): Future[Subscription]

  def onEvent[REQ <: Request[Body]](matcher: RequestMatcher,
              groupName: String,
              inputDeserializer: RequestDeserializer[REQ],
              observer: Observer[REQ]): Future[Subscription]

  def off(subscription: Subscription): Future[Unit]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

class NoTransportRouteException(message: String) extends RuntimeException(message)

class NoSuchHeaderException(header: String) extends RuntimeException(s"No such header: $header")