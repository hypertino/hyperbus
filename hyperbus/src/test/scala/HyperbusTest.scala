import java.util.concurrent.atomic.AtomicLong

import com.hypertino.binders.value._
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.{RequestMatcher, Specific}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import rx.lang.scala.Observer
import testclasses._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

//class ClientTransportTest(output: String) extends ClientTransport {
//  private val messageBuf = new StringBuilder
//
//  def input = messageBuf.toString()
//
//  override def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Future[ResponseBase] = {
//    messageBuf.append(message.serializeToString)
//
//    val out = MessageReader.from(output, responseDeserializer)
//    Future.successful(out)
//  }
//
//  override def publish(message: RequestBase): Future[PublishResult] = {
//    ask(message, null) map { x =>
//      new PublishResult {
//        def sent = None
//
//        def offset = None
//      }
//    }
//  }
//
//  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
//    Future.successful(true)
//  }
//}
//
//case class ServerHyperbusSubscriptionTest(id: String) extends HyperbusSubscription
//
//class ServerTransportTest extends ServerTransport {
//  //var sUriFilter: UriPattern = null
//  var sInputDeserializer: RequestDeserializer[Request[Body]] = null
//  var sHandler: (RequestBase) ⇒ Future[ResponseBase] = null
//  var sSubscriptionId: String = null
//  val idCounter = new AtomicLong(0)
//
//  override def onCommand[REQ <: Request[Body]](matcher: RequestMatcher,
//                         inputDeserializer: RequestDeserializer[REQ])
//                        (handler: (REQ) => Future[ResponseBase]): Future[HyperbusSubscription] = {
//
//    sInputDeserializer = inputDeserializer
//    sHandler = handler.asInstanceOf[(RequestBase) ⇒ Future[ResponseBase]]
//    Future {
//      ServerHyperbusSubscriptionTest(idCounter.incrementAndGet().toHexString)
//    }
//  }
//
//  override def onEvent[REQ <: Request[Body]](matcher: RequestMatcher,
//                       groupName: String,
//                       inputDeserializer: RequestDeserializer[REQ],
//                       subscriber: Observer[REQ]): Future[HyperbusSubscription] = {
//    sInputDeserializer = inputDeserializer
//    Future {
//      ServerHyperbusSubscriptionTest(idCounter.incrementAndGet().toHexString)
//    }
//  }
//
//  override def off(subscription: HyperbusSubscription): Future[Unit] = Future {
//    subscription match {
//      case ServerHyperbusSubscriptionTest(subscriptionId) ⇒
//        sSubscriptionId = subscriptionId
//        sInputDeserializer = null
//        sHandler = null
//    }
//  }
//
//  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
//    Future.successful(true)
//  }
//}
//
//class HyperbusTest extends FlatSpec with ScalaFutures with Matchers {
//  implicit val mcx = new MessagingContext {
//    override def createMessageId() = "123"
//    override def correlationId = None
//  }
//
//  "<~ " should "send a request (client)" in {
//    val ct = new ClientTransportTest(
//      """{"s":201,"t":"created-body","i":"123","l":{"a":"hb://test"}}""" + "\r\n" + """{"resourceId":"100500"}"""
//    )
//
//    val hyperbus = newHyperbus(ct, null)
//    val f = hyperbus <~ testclasses.TestPost1(testclasses.TestBody1("ha ha"))
//
//    ct.input should equal(
//      """{"r":{"a":"/resources"},"m":"post","t":"test-1","i":"123"}""" + "\r\n" + """{"resourceData":"ha ha"}"""
//    )
//
//    f.futureValue.body should equal(testclasses.TestCreatedBody("100500"))
//  }
//
//  "<~ " should "send a request, dynamic (client)" in {
//
//    val ct = new ClientTransportTest(
//      """{"s":201,"t":"created-body","i":"123"}""" + "\r\n" + """{"resourceId":"100500"}"""
//    )
//
//    val hyperbus = newHyperbus(ct, null)
//    val f = hyperbus <~ DynamicRequest(HRI("/resources"),
//      Method.POST,
//      DynamicBody(
//        Obj.from("resourceData" → "ha ha"),
//        Some("test-1")
//      )
//    )
//
//    ct.input should equal(
//      """{"r":{"a":"/resources"},"m":"post","t":"test-1","i":"123"}""" + "\r\n" + """{"resourceData":"ha ha"}"""
//    )
//
//    val r = f.futureValue
//    r shouldBe a[Created[_]]
//    r.body shouldBe a[DynamicBody]
//  }
//
//  "<~ " should " receive empty response (client)" in {
//    val ct = new ClientTransportTest(
//      """{"s":204,"i":"123"}"""
//    )
//
//    val hyperbus = newHyperbus(ct, null)
//    val f = hyperbus <~ TestPostWithNoContent(testclasses.TestBody1("empty"))
//
//    ct.input should equal(
//      """{"r":{"a":"/empty"},"m":"post","t":"test-1","i":"123"}""" + "\r\n" + """{"resourceData":"empty"}"""
//    )
//
//    val r = f.futureValue
//    r shouldBe a[NoContent[_]]
//    r.body shouldBe a[EmptyBody]
//  }
//
//  "<~ " should " send static request with dynamic body (client)" in {
//    val ct = new ClientTransportTest(
//      """{"s":204,"i":"123"}"""
//    )
//
//    val hyperbus = newHyperbus(ct, null)
//    val f = hyperbus <~ StaticPostWithDynamicBody(DynamicBody(Text("ha ha")))
//
//    ct.input should equal(
//      """{"r":{"a":"/empty"},"m":"post","i":"123"}""" + "\r\n" + """"ha ha""""
//    )
//
//    val r = f.futureValue
//    r shouldBe a[NoContent[_]]
//    r.body shouldBe a[EmptyBody]
//  }
//
//  "<~ " should " send static request with empty body (client)" in {
//    val ct = new ClientTransportTest(
//      """{"s":204,"i":"123"}"""
//    )
//
//    val hyperbus = newHyperbus(ct, null)
//    val f = hyperbus <~ StaticPostWithEmptyBody()
//
//    ct.input should equal(
//      """{"r":{"a":"/empty"},"m":"post","i":"123"}"""
//    )
//
//    val r = f.futureValue
//    r shouldBe a[NoContent[_]]
//    r.body shouldBe a[EmptyBody]
//  }
//
//  "<~" should "send static request with body without contentType specified" in {
//    val ct = new ClientTransportTest(
//      """{"s":204,"i":"123"}"""
//    )
//
//    val hyperbus = newHyperbus(ct, null)
//    val f = hyperbus <~ StaticPostBodyWithoutContentType(TestBodyNoContentType("yey"))
//
//    ct.input should equal(
//      """{"r":{"a":"/content-body-not-specified"},"m":"post","i":"123"}""" + "\r\n" + """{"resourceData":"yey"}"""
//    )
//
//    val r = f.futureValue
//    r shouldBe a[NoContent[_]]
//    r.body shouldBe a[EmptyBody]
//  }
//
//  "<~" should "send static request with some query (client)" in {
//    val ct = new ClientTransportTest(
//      """{"s":200,"i":"123"}""" + "\r\n" + """{"data":"abc"}"""
//    )
//
//    val hyperbus = newHyperbus(ct, null)
//    val f = hyperbus <~ StaticGetWithQuery()
//
//    ct.input should equal(
//      """{"r":{"a":"/empty"},"m":"get","i":"123"}"""
//    )
//
//    val r = f.futureValue
//    r shouldBe a[Ok[_]]
//    r.body shouldBe a[DynamicBody]
//  }
//
//  "<~" should "catch client exception" in {
//    val ct = new ClientTransportTest(
//      """{"s":409,"i":"abcde12345"}""" + "\r\n" + """{"code":"failed","errorId":"abcde12345"}"""
//    )
//
//    val hyperbus = newHyperbus(ct, null)
//    val f = hyperbus <~ testclasses.TestPost1(testclasses.TestBody1("ha ha"))
//
//    ct.input should equal(
//      """{"r":{"a":"/resources"},"m":"post","t":"test-1","i":"123"}""" + "\r\n" + """{"resourceData":"ha ha"}"""
//    )
//
//    val r = f.failed.futureValue
//    r shouldBe a[Conflict[_]]
//    r.asInstanceOf[Conflict[_]].body should equal(ErrorBody("failed", errorId = "abcde12345"))
//  }
//
//  "~>" should "call method for server request" in {
//    val st = new ServerTransportTest()
//    val hyperbus = newHyperbus(null, st)
//    hyperbus ~> { post: testclasses.TestPost1 =>
//      Future {
//        Created(testclasses.TestCreatedBody("100500"))
//      }
//    }
//
//    val msg = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
//
//    val futureResult = st.sHandler(msg)
//    futureResult.futureValue should equal(Created(testclasses.TestCreatedBody("100500")))
//  }
//
//  "~>" should "call method for server request when missed a contentType" in {
//    val st = new ServerTransportTest()
//    val hyperbus = newHyperbus(null, st)
//    hyperbus ~> { post: testclasses.TestPost1 =>
//      Future {
//        Created(testclasses.TestCreatedBody("100500"))
//      }
//    }
//
//    val orig = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
//    val msg = orig.copy(
//      headers = RequestHeaders(orig.headers.all - Header.CONTENT_TYPE)
//    )
//    msg.headers.contentType shouldBe empty
//
//    val r = st.sHandler(msg).futureValue
//    r should equal(Created(testclasses.TestCreatedBody("100500")))
//  }
//
//  "~>" should "call method for static request with empty body (server)" in {
//    val st = new ServerTransportTest()
//    val hyperbus = newHyperbus(null, st)
//    hyperbus ~> { post: StaticPostWithEmptyBody =>
//      Future {
//        NoContent(EmptyBody)
//      }
//    }
//
//    val msg = StaticPostWithEmptyBody(EmptyBody)
//    val r = st.sHandler(msg).futureValue
//    r should equal(NoContent(EmptyBody))
//  }
//
//  "~>" should "call method for static request with dynamic body (server)" in {
//    val st = new ServerTransportTest()
//    val hyperbus = newHyperbus(null, st)
//    hyperbus ~> { post: StaticPostWithDynamicBody =>
//      Future {
//        NoContent(EmptyBody)
//      }
//    }
//
//    val msg = StaticPostWithDynamicBody(DynamicBody("haha", Some("some-content")))
//    val r =  st.sHandler(msg).futureValue
//    r should equal(NoContent(EmptyBody))
//  }
//
//  "~>" should "call method for dynamic request (server)" in {
//    val st = new ServerTransportTest()
//    val hyperbus = newHyperbus(null, st)
//    hyperbus.onCommand(RequestMatcher("/test", Method.GET, None)) { request =>
//      Future {
//        NoContent(EmptyBody)
//      }
//    }
//
//    val msg = DynamicRequest(
//      HRI("/test"),
//      Method.GET,
//      DynamicBody("haha", Some("some-content")),
//      Obj.from(
//        Header.CONTENT_TYPE → "some-content",
//        Header.MESSAGE_ID → "123"
//      )
//    )
//
//    val r = st.sHandler(msg).futureValue
//    r shouldBe a[NoContent[_]]
//    r.asInstanceOf[NoContent[_]].body shouldBe a[EmptyBody]
//  }
//
//  "<|" should "publish static request (client)" in {
//    val rsp = """{"status":409,"headers":{"messageId":"123"},"body":{"code":"failed","errorId":"abcde12345"}}"""
//    var sentEvents = List[RequestBase]()
//    val clientTransport = new ClientTransportTest(rsp) {
//      override def publish(message: RequestBase): Future[PublishResult] = {
//        Future {
//          sentEvents = sentEvents :+ message
//          new PublishResult {
//            def sent = None
//
//            def offset = None
//          }
//        }
//      }
//    }
//
//    val hyperbus = newHyperbus(clientTransport, null)
//    val futureResult = hyperbus <| testclasses.TestPost1(testclasses.TestBody1("ha ha"))
//    futureResult.futureValue
//    sentEvents.size should equal(1)
//  }
//
//  "<|" should "publish dynamic request (client)" in {
//    val rsp = """{"status":409,"headers":{"messageId":"123"},"body":{"code":"failed","errorId":"abcde12345"}}"""
//    var sentEvents = List[RequestBase]()
//    val clientTransport = new ClientTransportTest(rsp) {
//      override def publish(message: RequestBase): Future[PublishResult] = {
//        Future {
//          sentEvents = sentEvents :+ message
//          new PublishResult {
//            def sent = None
//
//            def offset = None
//          }
//        }
//      }
//    }
//
//    val hyperbus = newHyperbus(clientTransport, null)
//    val futureResult = hyperbus <| DynamicRequest(HRI("/resources"), Method.POST,
//      DynamicBody(Obj.from("resourceData" → "ha ha"), Some("test-1")))
//
//    futureResult.futureValue
//    sentEvents.size should equal(1)
//  }
//
//  "|>" should "call method for static request subscription (server)" in {
//    var receivedEvents = 0
//    val serverTransport = new ServerTransportTest() {
//      override def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
//                                                 groupName: String,
//                                                 inputDeserializer: RequestDeserializer[REQ],
//                                                 subscriber: Observer[REQ]): Future[HyperbusSubscription] = {
//        receivedEvents += 1
//        super.onEvent(requestMatcher, groupName, inputDeserializer, subscriber)
//      }
//    }
//    val hyperbus = newHyperbus(null, serverTransport)
//    val observer = new Observer[testclasses.TestPost1] {}
//
//    hyperbus |> observer
//    receivedEvents should equal(1)
//  }
//
//  "|>" should "call method for dynamic request subscription (server)" in {
//    var receivedEvents = 0
//    val serverTransport = new ServerTransportTest() {
//      override def onEvent[REQ <: Request[Body]](requestMatcher: RequestMatcher,
//                                                 groupName: String,
//                                                 inputDeserializer: RequestDeserializer[REQ],
//                                                 subscriber: Observer[REQ]): Future[HyperbusSubscription] = {
//        receivedEvents += 1
//        super.onEvent(requestMatcher, groupName, inputDeserializer, subscriber)
//      }
//    }
//    val hyperbus = newHyperbus(null, serverTransport)
//    val observer = new Observer[DynamicRequest] {}
//
//    hyperbus.onEvent(
//      RequestMatcher("/test", Method.GET),
//      Some("group1"),
//      observer
//    )
//    receivedEvents should equal(1)
//  }
//
//  "~>" should "handle exception thrown" in {
//    val st = new ServerTransportTest()
//    val hyperbus = newHyperbus(null, st)
//    hyperbus ~> { post: testclasses.TestPost1 =>
//      Future {
//        throw Conflict(ErrorBody("failed", errorId = "abcde12345"))
//      }
//    }
//
//    val msg = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
//
//    val r = st.sHandler(msg).futureValue
//    r shouldBe a[Conflict[_]]
//    r.asInstanceOf[Conflict[_]].body should equal(ErrorBody("failed", errorId = "abcde12345"))
//  }
//
//  "off" should "disable ~> subscription" in {
//    val st = new ServerTransportTest()
//    val hyperbus = newHyperbus(null, st)
//    val id1f = hyperbus ~> { post: TestPostWithNoContent =>
//      Future {
//        NoContent(EmptyBody)
//      }
//    }
//    val id1 = id1f.futureValue
//
//    st.sHandler shouldNot equal(null)
//    hyperbus.off(id1).futureValue
//    st.sHandler should equal(null)
//    st.sSubscriptionId should equal("1")
//
//    val id2f = hyperbus ~> { post: TestPostWithNoContent =>
//      Future {
//        NoContent(EmptyBody)
//      }
//    }
//    val id2 = id2f.futureValue
//
//    st.sHandler shouldNot equal(null)
//    hyperbus.off(id2).futureValue
//    st.sHandler should equal(null)
//    st.sSubscriptionId should equal("2")
//  }
//
//  "off" should " disable |> subscription" in {
//    val st = new ServerTransportTest()
//    val hyperbus = newHyperbus(null, st)
//    val observer = new Observer[testclasses.TestPost1] {}
//
//    val id1f = hyperbus |> observer
//    val id1 = id1f.futureValue
//
//    hyperbus.off(id1).futureValue
//    st.sSubscriptionId should equal("1")
//
//    val id2f = hyperbus |> observer
//    val id2 = id2f.futureValue
//
//    hyperbus.off(id2).futureValue
//    st.sSubscriptionId should equal("2")
//  }
//
//  def newHyperbus(ct: ClientTransport, st: ServerTransport) = {
//    val cr = List(TransportRoute(ct, RequestMatcher.any))
//    val sr = List(TransportRoute(st, RequestMatcher.any))
//    val transportManager = new TransportManager(cr, sr, ExecutionContext.global)
//    new Hyperbus(transportManager, Some("group1"), logMessages = true)
//  }
//}
