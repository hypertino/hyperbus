import java.io.{ByteArrayInputStream, ByteArrayOutputStream, StringReader}
import java.util.concurrent.atomic.AtomicLong

import com.hypertino.binders.value._
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.{Any, RequestMatcher, Specific}
import com.hypertino.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, FreeSpec, Matchers}
import rx.lang.scala.Observer
import testclasses._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class ClientTransportTest(output: String) extends ClientTransport {
  private val messageBuf = new StringBuilder

  def input = messageBuf.toString()

  override def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse] = {
    messageBuf.append(message.serializeToString)

    val out = outputDeserializer(new StringReader(output))
    Future.successful(out)
  }

  override def publish(message: TransportRequest): Future[PublishResult] = {
    ask(message, null) map { x =>
      new PublishResult {
        def sent = None

        def offset = None
      }
    }
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    Future.successful(true)
  }
}

case class ServerSubscriptionTest(id: String) extends Subscription

class ServerTransportTest extends ServerTransport {
  var sUriFilter: Uri = null
  var sInputDeserializer: RequestDeserializer[Request[Body]] = null
  var sHandler: (TransportRequest) ⇒ Future[TransportResponse] = null
  var sSubscriptionId: String = null
  val idCounter = new AtomicLong(0)

  override def onCommand[REQ <: Request[Body]](matcher: RequestMatcher,
                         inputDeserializer: RequestDeserializer[REQ])
                        (handler: (REQ) => Future[TransportResponse]): Future[Subscription] = {

    sInputDeserializer = inputDeserializer
    sHandler = handler.asInstanceOf[(TransportRequest) ⇒ Future[TransportResponse]]
    Future {
      ServerSubscriptionTest(idCounter.incrementAndGet().toHexString)
    }
  }

  override def onEvent[REQ <: Request[Body]](matcher: RequestMatcher,
                       groupName: String,
                       inputDeserializer: RequestDeserializer[REQ],
                       subscriber: Observer[REQ]): Future[Subscription] = {
    sInputDeserializer = inputDeserializer
    Future {
      ServerSubscriptionTest(idCounter.incrementAndGet().toHexString)
    }
  }

  override def off(subscription: Subscription): Future[Unit] = Future {
    subscription match {
      case ServerSubscriptionTest(subscriptionId) ⇒
        sSubscriptionId = subscriptionId
        sInputDeserializer = null
        sHandler = null
    }
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    Future.successful(true)
  }
}

class HyperbusTest extends FlatSpec with ScalaFutures with Matchers {
  implicit val mcx = new MessagingContext {
    override def createMessageId() = "123"
    override def correlationId = None
  }

  "<~ " should "send a request (client)" in {
    val ct = new ClientTransportTest(
      """{"status":201,"headers":{"contentType":"created-body","messageId":"123"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus <~ testclasses.TestPost1(testclasses.TestBody1("ha ha"))

    ct.input should equal(
      """{"uri":{"pattern":"/resources"},"headers":{"messageId":"123","method":"post","contentType":"test-1"},"body":{"resourceData":"ha ha"}}"""
    )

    whenReady(f) { r =>
      r.body should equal(testclasses.TestCreatedBody("100500"))
    }
  }

  "<~ " should "send a request, dynamic (client)" in {

    val ct = new ClientTransportTest(
      """{"status":201,"headers":{"contentType":["created-body"],"messageId":"123"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus <~ DynamicRequest(Uri("/resources"),
      Method.POST,
      DynamicBody(
        Some("test-1"),
        ObjV("resourceData" → "ha ha")
      )
    )

    ct.input should equal(
      """{"uri":{"pattern":"/resources"},"headers":{"messageId":"123","method":"post","contentType":"test-1"},"body":{"resourceData":"ha ha"}}"""
    )

    whenReady(f) { r =>
      r shouldBe a[Created[_]]
      r.body shouldBe a[DynamicBody]
      r.body shouldBe a[CreatedBody]
    }
  }

  "<~ " should " send empty request (client)" in {
    val ct = new ClientTransportTest(
      """{"status":204,"headers":{"messageId":"123"},"body":{}}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus <~ TestPostWithNoContent(testclasses.TestBody1("empty"))

    ct.input should equal(
      """{"uri":{"pattern":"/empty"},"headers":{"messageId":"123","method":"post","contentType":"test-1"},"body":{"resourceData":"empty"}}"""
    )

    whenReady(f) { r =>
      r shouldBe a[NoContent[_]]
      r.body shouldBe a[EmptyBody]
    }
  }

  "<~ " should " send static request with dynamic body (client)" in {
    val ct = new ClientTransportTest(
      """{"status":204,"headers":{"messageId":"123"},"body":{}}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus <~ StaticPostWithDynamicBody(DynamicBody(Text("ha ha")))

    ct.input should equal(
      """{"uri":{"pattern":"/empty"},"headers":{"messageId":"123","method":"post"},"body":"ha ha"}"""
    )

    whenReady(f) { r =>
      r shouldBe a[NoContent[_]]
      r.body shouldBe a[EmptyBody]
    }
  }

  "<~ " should " send static request with empty body (client)" in {
    val ct = new ClientTransportTest(
      """{"status":204,"headers":{"messageId":"123"},"body":{}}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus <~ StaticPostWithEmptyBody()

    ct.input should equal(
      """{"uri":{"pattern":"/empty"},"headers":{"messageId":"123","method":"post"},"body":null}"""
    )

    whenReady(f) { r =>
      r shouldBe a[NoContent[_]]
      r.body shouldBe a[EmptyBody]
    }
  }

  "<~" should "send static request with body without contentType specified" in {
    val ct = new ClientTransportTest(
      """{"status":204,"headers":{"messageId":"123"},"body":{}}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus <~ StaticPostBodyWithoutContentType(TestBodyNoContentType("yey"))

    ct.input should equal(
      """{"uri":{"pattern":"/content-body-not-specified"},"headers":{"messageId":"123","method":"post"},"body":{"resourceData":"yey"}}"""
    )

    whenReady(f) { r =>
      r shouldBe a[NoContent[_]]
      r.body shouldBe a[EmptyBody]
    }
  }

  "<~" should "send static request with query body (client)" in {
    val ct = new ClientTransportTest(
      """{"status":200,"headers":{"messageId":"123"},"body":{"data":"abc"}}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus <~ StaticGetWithQuery()

    ct.input should equal(
      """{"uri":{"pattern":"/empty"},"headers":{"messageId":"123","method":"get"},"body":null}"""
    )

    whenReady(f) { r =>
      r shouldBe a[Ok[_]]
      r.body shouldBe a[DynamicBody]
    }
  }

  "<~" should "catch client exception" in {
    val ct = new ClientTransportTest(
      """{"status":409,"headers":{"messageId":"abcde12345"},"body":{"code":"failed","errorId":"abcde12345"}}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus <~ testclasses.TestPost1(testclasses.TestBody1("ha ha"))

    ct.input should equal(
      """{"uri":{"pattern":"/resources"},"headers":{"messageId":"123","method":"post","contentType":"test-1"},"body":{"resourceData":"ha ha"}}"""
    )

    whenReady(f.failed) { r =>
      r shouldBe a[Conflict[_]]
      r.asInstanceOf[Conflict[_]].body should equal(ErrorBody("failed", errorId = "abcde12345"))
    }
  }

  "~>" should "call method for server request" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus ~> { post: testclasses.TestPost1 =>
      Future {
        Created(testclasses.TestCreatedBody("100500"))
      }
    }

    val req = """{"uri":{"pattern":"/resources"},"headers":{"method":"post","contentType":"test-1","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
    val msg = MessageDeserializer.deserializeRequestWith(req)(st.sInputDeserializer)
    msg should equal(testclasses.TestPost1(testclasses.TestBody1("ha ha")))

    val futureResult = st.sHandler(msg)
    whenReady(futureResult) { r =>
      r should equal(Created(testclasses.TestCreatedBody("100500")))
    }
  }

  "~>" should "call method for server request when missed a contentType" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus ~> { post: testclasses.TestPost1 =>
      Future {
        Created(testclasses.TestCreatedBody("100500"))
      }
    }

    val req = """{"uri":{"pattern":"/resources"},"headers":{"method":"post","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
    val msg = MessageDeserializer.deserializeRequestWith(req)(st.sInputDeserializer)
    msg shouldBe a[testclasses.TestPost1]
    msg.body shouldBe a[testclasses.TestBody1]
    msg.body.asInstanceOf[testclasses.TestBody1].resourceData should equal("ha ha")

    val futureResult = st.sHandler(msg)
    whenReady(futureResult) { r =>
      r should equal(Created(testclasses.TestCreatedBody("100500")))
    }
  }

  "~>" should "call method for static request with empty body (server)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus ~> { post: StaticPostWithEmptyBody =>
      Future {
        NoContent(EmptyBody)
      }
    }

    val req = """{"uri":{"pattern":"/empty"},"headers":{"method":"post","messageId":"123"},"body":null}"""
    val msg = MessageDeserializer.deserializeRequestWith(req)(st.sInputDeserializer)
    msg should equal(StaticPostWithEmptyBody(EmptyBody))

    val futureResult = st.sHandler(msg)
    whenReady(futureResult) { r =>
      r should equal(NoContent(EmptyBody))
    }
  }

  "~>" should "call method for static request with dynamic body (server)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus ~> { post: StaticPostWithDynamicBody =>
      Future {
        NoContent(EmptyBody)
      }
    }

    val req = """{"uri":{"pattern":"/empty"},"headers":{"method":"post","contentType":"some-content","messageId":"123"},"body":"haha"}"""
    val msg = MessageDeserializer.deserializeRequestWith(req)(st.sInputDeserializer)
    msg should equal(StaticPostWithDynamicBody(DynamicBody(Some("some-content"), Text("haha"))))

    val futureResult = st.sHandler(msg)
    whenReady(futureResult) { r =>
      r should equal(NoContent(EmptyBody))
    }
  }

  "~>" should "call method for dynamic request (server)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.onCommand(RequestMatcher(
      Some(Uri("/test")),
      Map(Header.METHOD → Specific(Method.GET)))
    ) { request =>
      Future {
        NoContent(EmptyBody)
      }
    }

    val req = """{"uri":{"pattern":"/test"},"headers":{"method":"get","contentType":"some-content","messageId":"123"},"body":"haha"}"""
    val msg = MessageDeserializer.deserializeRequestWith(req)(st.sInputDeserializer)
    msg should equal(DynamicRequest(
      RequestHeader(Uri("/test"), Map(
        Header.METHOD → Method.GET,
        Header.CONTENT_TYPE → "some-content",
        Header.MESSAGE_ID → "123")
      ),
      DynamicBody(Some("some-content"), Text("haha")))
    )

    val futureResult = st.sHandler(msg)
    whenReady(futureResult) { r =>
      r shouldBe a[NoContent[_]]
      r.asInstanceOf[NoContent[_]].body shouldBe a[EmptyBody]
    }
  }

  "<|" should "publish static request (client)" in {
    val rsp = """{"status":409,"headers":{"messageId":"123"},"body":{"code":"failed","errorId":"abcde12345"}}"""
    var sentEvents = List[TransportRequest]()
    val clientTransport = new ClientTransportTest(rsp) {
      override def publish(message: TransportRequest): Future[PublishResult] = {
        Future {
          sentEvents = sentEvents :+ message
          new PublishResult {
            def sent = None

            def offset = None
          }
        }
      }
    }

    val hyperbus = newHyperbus(clientTransport, null)
    val futureResult = hyperbus <| testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    whenReady(futureResult) { r =>
      sentEvents.size should equal(1)
    }
  }

  "<|" should "publish dynamic request (client)" in {
    val rsp = """{"status":409,"headers":{"messageId":"123"},"body":{"code":"failed","errorId":"abcde12345"}}"""
    var sentEvents = List[TransportRequest]()
    val clientTransport = new ClientTransportTest(rsp) {
      override def publish(message: TransportRequest): Future[PublishResult] = {
        Future {
          sentEvents = sentEvents :+ message
          new PublishResult {
            def sent = None

            def offset = None
          }
        }
      }
    }

    val hyperbus = newHyperbus(clientTransport, null)
    val futureResult = hyperbus <| DynamicRequest(Uri("/resources"), Method.POST,
      DynamicBody(Some("test-1"), ObjV("resourceData" → "ha ha")))
    whenReady(futureResult) { r =>
      sentEvents.size should equal(1)
    }
  }

  "|>" should "call method for static request subscription (server)" in {
    var receivedEvents = 0
    val serverTransport = new ServerTransportTest() {
      override def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                                 groupName: String,
                                                 inputDeserializer: RequestDeserializer[REQ],
                                                 subscriber: Observer[REQ]): Future[Subscription] = {
        receivedEvents += 1
        super.onEvent(requestMatcher, groupName, inputDeserializer, subscriber)
      }
    }
    val hyperbus = newHyperbus(null, serverTransport)
    val observer = new Observer[testclasses.TestPost1] {}

    hyperbus |> observer

    receivedEvents should equal(1)
  }

  "|>" should "call method for dynamic request subscription (server)" in {
    var receivedEvents = 0
    val serverTransport = new ServerTransportTest() {
      override def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
                                                 groupName: String,
                                                 inputDeserializer: RequestDeserializer[REQ],
                                                 subscriber: Observer[REQ]): Future[Subscription] = {
        receivedEvents += 1
        super.onEvent(requestMatcher, groupName, inputDeserializer, subscriber)
      }
    }
    val hyperbus = newHyperbus(null, serverTransport)
    val observer = new Observer[DynamicRequest] {}

    hyperbus.onEvent(
      RequestMatcher(
        Some(Uri("/test")),
        Map(Header.METHOD → Specific(Method.GET))),
      Some("group1"),
      observer
    )
    receivedEvents should equal(1)
  }

  "~>" should "handle exception thrown" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus ~> { post: testclasses.TestPost1 =>
      Future {
        throw Conflict(ErrorBody("failed", errorId = "abcde12345"))
      }
    }

    val req = """{"uri":{"pattern":"/resources"},"headers":{"messageId":"123","method":"post","contentType":"test-1"},"body":{"resourceData":"ha ha"}}"""
    val msg = MessageDeserializer.deserializeRequestWith(req)(st.sInputDeserializer)
    msg should equal(testclasses.TestPost1(testclasses.TestBody1("ha ha")))

    val futureResult = st.sHandler(msg)
    whenReady(futureResult) { r =>
      r shouldBe a[Conflict[_]]
      r.serializeToString should equal(
        """{"status":409,"headers":{"messageId":"123"},"body":{"code":"failed","errorId":"abcde12345"}}"""
      )
    }
  }

  "off" should "disable ~> subscription" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    val id1f = hyperbus ~> { post: TestPostWithNoContent =>
      Future {
        NoContent(EmptyBody)
      }
    }
    val id1 = id1f.futureValue

    st.sHandler shouldNot equal(null)
    hyperbus.off(id1).futureValue
    st.sHandler should equal(null)
    st.sSubscriptionId should equal("1")

    val id2f = hyperbus ~> { post: TestPostWithNoContent =>
      Future {
        NoContent(EmptyBody)
      }
    }
    val id2 = id2f.futureValue

    st.sHandler shouldNot equal(null)
    hyperbus.off(id2).futureValue
    st.sHandler should equal(null)
    st.sSubscriptionId should equal("2")
  }

  "off" should " disable |> subscription" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    val observer = new Observer[testclasses.TestPost1] {}

    val id1f = hyperbus |> observer
    val id1 = id1f.futureValue

    hyperbus.off(id1).futureValue
    st.sSubscriptionId should equal("1")

    val id2f = hyperbus |> observer
    val id2 = id2f.futureValue

    hyperbus.off(id2).futureValue
    st.sSubscriptionId should equal("2")
  }

  def newHyperbus(ct: ClientTransport, st: ServerTransport) = {
    val cr = List(TransportRoute(ct, RequestMatcher(Some(Uri(Any)))))
    val sr = List(TransportRoute(st, RequestMatcher(Some(Uri(Any)))))
    val transportManager = new TransportManager(cr, sr, ExecutionContext.global)
    new Hyperbus(transportManager, Some("group1"), logMessages = true)
  }
}
