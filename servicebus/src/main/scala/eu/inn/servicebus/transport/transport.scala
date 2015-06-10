package eu.inn.servicebus.transport

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.typesafe.config.Config
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.util.ConfigUtils._
import eu.inn.servicebus.util.Subscriptions
import org.slf4j.LoggerFactory

import scala.Option
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.matching.Regex

trait PartitionArg {
  def matchArg(arg: PartitionArg): Boolean
}

case object AnyArg extends PartitionArg {
  def matchArg(arg: PartitionArg) = true
}

case class ExactArg(value: String) extends PartitionArg {
  def matchArg(other: PartitionArg) = other match {
    case ExactArg(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchArg(this)
  }
}

case class RegexArg(value: String) extends PartitionArg {
  lazy val valueRegex = new Regex(value)
  def matchArg(other: PartitionArg) = other match {
    case ExactArg(otherValue) ⇒ valueRegex.findFirstMatchIn(otherValue).isDefined
    case RegexArg(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchArg(this)
  }
}

// case class ExactPartition(partition: String) extends PartitionArg -- kafka?

case class PartitionArgs(args: Map[String, PartitionArg]) {
  def matchArgs(other: PartitionArgs): Boolean = {
    args.map { case (k, v) ⇒
      other.args.get(k).map { av ⇒
        av.matchArg(v)
      } getOrElse {
        v == AnyArg
      }
    }.forall(r => r)
  }
}

case class Topic(url: String, partitionArgs: PartitionArgs)

trait ClientTransport {
  def ask[OUT, IN](
                    topic: Topic,
                    message: IN,
                    inputEncoder: Encoder[IN],
                    outputDecoder: Decoder[OUT]
                    ): Future[OUT]

  def publish[IN](
                   topic: Topic,
                   message: IN,
                   inputEncoder: Encoder[IN]
                   ): Future[Unit]

  def shutdown(duration: Duration): Future[Boolean]
}

case class SubscriptionHandlerResult[OUT](futureResult: Future[OUT], resultEncoder: Encoder[OUT])

trait ServerTransport {
  def on[OUT, IN](topic: Topic,
                  inputDecoder: Decoder[IN],
                  partitionArgsExtractor: PartitionArgsExtractor[IN],
                  exceptionEncoder: Encoder[Throwable])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def subscribe[IN](topic: Topic, groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: PartitionArgsExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String // todo: Unit -> some useful response?

  def off(subscriptionId: String)
  def shutdown(duration: Duration): Future[Boolean]
}

private[transport] case class SubKey(groupName: Option[String], partitionArgs: PartitionArgs)

private[transport] case class Subscription[OUT, IN](inputDecoder: Decoder[IN],
                                                     partitionArgsExtractor: PartitionArgsExtractor[IN],
                                                     exceptionEncoder: Encoder[Throwable],
                                                     handler: (IN) => SubscriptionHandlerResult[OUT])

class NoTransportRouteException(message: String) extends RuntimeException(message)

class InprocTransport(serialize: Boolean = false)
                     (implicit val executionContext: ExecutionContext) extends ClientTransport with ServerTransport {

  def this(config: Config) = this(config.getOptionBoolean("serialize").getOrElse(false))(
    scala.concurrent.ExecutionContext.global // todo: configurable ExecutionContext like in akka?
  )

  protected val subscriptions = new Subscriptions[SubKey, Subscription[_, _]]
  protected val log = LoggerFactory.getLogger(this.getClass)

  def reencodeMessage[IN,OUT](message: IN, encoder: Encoder[IN], decoder: Decoder[OUT]): OUT = {
    if (serialize) {
      val ba = new ByteArrayOutputStream()
      encoder(message, ba)
      val bi = new ByteArrayInputStream(ba.toByteArray)
      decoder(bi)
    }
    else {
      message.asInstanceOf[OUT]
    }
  }

  override def ask[OUT, IN](
                             topic: Topic,
                             message: IN,
                             inputEncoder: Encoder[IN],
                             outputDecoder: Decoder[OUT]
                             ): Future[OUT] = {
    var result: Future[OUT] = null

    def tryX[T] (failMsg: String, exceptionEncoder: Encoder[Throwable], code: ⇒ T): Option[T] = {
      try {
        Some(code)
      }
      catch {
        case NonFatal(e) ⇒
          result =
            if (serialize)
              Future.successful {
                reencodeMessage(e, exceptionEncoder, outputDecoder)
              }
            else
              Future.failed {
                e
              }
          log.error(failMsg, e)
          None
      }
    }

    // todo: filter is redundant for inproc?
    subscriptions.get(topic.url).subRoutes filter (_._1.partitionArgs.matchArgs(topic.partitionArgs)) foreach {
      case (subKey, subscriptionList) =>

        if (subKey.groupName.isEmpty) {
          // default subscription (groupName="") returns reply
          val subscriber = subscriptionList.getRandomSubscription.asInstanceOf[Subscription[OUT, IN]]

          tryX ("Decode failed", subscriber.exceptionEncoder,
            reencodeMessage(message, inputEncoder, subscriber.inputDecoder)
          ) foreach { messageForSubscriber ⇒

            tryX ("Decode failed", subscriber.exceptionEncoder,
              subscriber.partitionArgsExtractor(messageForSubscriber)
            ) foreach { args ⇒

              if (subKey.partitionArgs.matchArgs(args)) {
                // todo: log if not matched?
                val handlerResult = subscriber.handler(messageForSubscriber)
                result = if (serialize) {
                  handlerResult.futureResult map { out ⇒
                    reencodeMessage(out, handlerResult.resultEncoder, outputDecoder)
                  } recoverWith {
                    case NonFatal(e) ⇒
                      log.error("`on` handler failed with", e)
                      Future.successful {
                        reencodeMessage(e, subscriber.exceptionEncoder, outputDecoder)
                      }
                  }
                }
                else {
                  handlerResult.futureResult
                }

                if (log.isTraceEnabled) {
                  log.trace(s"Message ($messageForSubscriber) is delivered to `on` @$subKey}")
                }
              }
            }
          }
        } else {
          val subscriber = subscriptionList.getRandomSubscription.asInstanceOf[Subscription[Unit, IN]]

          val ma =
            try {
              val messageForSubscriber = reencodeMessage(message, inputEncoder, subscriber.inputDecoder)
              val args = subscriber.partitionArgsExtractor(messageForSubscriber)
              Some((messageForSubscriber, args))
            }
            catch {
              case NonFatal(e) ⇒
                log.error("`subscription` decoder failed with", e)
                None
            }

          ma.foreach { case (messageForSubscriber, args) ⇒
            if (subKey.partitionArgs.matchArgs(args)) {
              // todo: log if not matched?
              subscriber.handler(messageForSubscriber).futureResult.onFailure {
                case NonFatal(e) ⇒
                  log.error("`subscription` handler failed with", e)
              }

              if (result == null) {
                result = Future.successful({}.asInstanceOf[OUT])
              }
              if (log.isTraceEnabled) {
                log.trace(s"Message ($messageForSubscriber) is delivered to `subscriber` @$subKey}")
              }
            }
          }
        }
    }

    if (result == null) {
      Future.failed[OUT](new NoTransportRouteException(s"Subscription on '$topic' isn't found"))
    }
    else {
      result
    }
  }

  def publish[IN](
                   topic: Topic,
                   message: IN,
                   inputEncoder: Encoder[IN]
                   ): Future[Unit] = {
    ask[Any, IN](topic, message, inputEncoder, null) map { x =>
    }
  }

  def on[OUT, IN](topic: Topic,
                  inputDecoder: Decoder[IN],
                  partitionArgsExtractor: PartitionArgsExtractor[IN],
                  exceptionEncoder: Encoder[Throwable])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {
    subscriptions.add(
      topic.url,
      SubKey(None, topic.partitionArgs),
      Subscription[OUT, IN](inputDecoder, partitionArgsExtractor, exceptionEncoder, handler)
    )
  }

  def subscribe[IN](topic: Topic,
                    groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: PartitionArgsExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String = {
    subscriptions.add(
      topic.url,
      SubKey(Some(groupName), topic.partitionArgs),
      Subscription[Unit, IN](inputDecoder, partitionArgsExtractor, null, handler)
    )
  }

  def off(subscriptionId: String) = {
    subscriptions.remove(subscriptionId)
  }

  def shutdown(duration: Duration): Future[Boolean] = {
    subscriptions.clear()
    Future.successful(true)
  }
}