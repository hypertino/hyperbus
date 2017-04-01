import java.util.concurrent.atomic.AtomicLong

import com.hypertino.binders.value.{Null, Obj, Text}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.{Cancelable, Scheduler}
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.ConcurrentSubject
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FlatSpec, Matchers}
import scaldi.Module
import testclasses.{TestPost1, _}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}

class ClientTransportTest(output: String) extends ClientTransport {
  private val messageBuf = new StringBuilder

  def input = messageBuf.toString()

  override def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Task[ResponseBase] = {
    messageBuf.append(message.serializeToString)

    val out = MessageReader.from(output, responseDeserializer)
    Task.now(out)
  }

  override def publish(message: RequestBase): Task[PublishResult] = {
    ask(message, null) map { x =>
      new PublishResult {
        def sent = None

        def offset = None
      }
    }
  }

  override def shutdown(duration: FiniteDuration): Task[Boolean] = {
    Task.now(true)
  }
}

//case class ServerHyperbusSubscriptionTest(id: String) extends HyperbusSubscription

class ServerTransportTest extends ServerTransport {
  var sMatcher: RequestMatcher = _
  var sInputDeserializer: RequestBaseDeserializer = _
  var sGroupName: String = _
  var sCommandsSubject: ConcurrentSubject[CommandEvent[RequestBase],CommandEvent[RequestBase]] = _
  var sEventsSubject: ConcurrentSubject[RequestBase,RequestBase] = _

  val idCounter = new AtomicLong(0)

  override def commands[REQ <: RequestBase](matcher: RequestMatcher,
                                              inputDeserializer: RequestDeserializer[REQ]): Observable[CommandEvent[REQ]] = {
    this.sMatcher = matcher
    this.sInputDeserializer = inputDeserializer
    sCommandsSubject = ConcurrentSubject.publishToOne[CommandEvent[RequestBase]]
    val observable: Observable[CommandEvent[RequestBase]] = (subscriber: Subscriber[CommandEvent[RequestBase]]) => {
      val original: Cancelable = sCommandsSubject.unsafeSubscribeFn(subscriber)
      () => {
        sCommandsSubject = null
        original.cancel()
      }
    }
    observable.asInstanceOf[Observable[CommandEvent[REQ]]]
  }

  override def events[REQ <: RequestBase](matcher: RequestMatcher,
                                            groupName: String,
                                            inputDeserializer: RequestDeserializer[REQ]): Observable[REQ] = {
    this.sMatcher = matcher
    this.sInputDeserializer = inputDeserializer
    this.sGroupName = groupName

    sEventsSubject = ConcurrentSubject.publishToOne[RequestBase]
    val observable: Observable[RequestBase] = (subscriber: Subscriber[RequestBase]) => {
      val original: Cancelable = sEventsSubject.unsafeSubscribeFn(subscriber)
      () => {
        sEventsSubject = null
        original.cancel()
      }
    }
    observable.asInstanceOf[Observable[REQ]]
  }

  override def shutdown(duration: FiniteDuration): Task[Boolean] = {
    Task.now(true)
  }

  def testCommand(msg: RequestBase): Task[ResponseBase] = {
    val c = CommandEvent(msg, Promise())
    sCommandsSubject.onNext(c)
    Task.fromFuture(c.responsePromise.future)
  }

  def testEvent(msg: RequestBase): Task[PublishResult] = {
    sEventsSubject.onNext(msg)
    Task.now {
      new PublishResult {
        override def offset: Option[String] = None
        override def sent: Option[Boolean] = Some(true)
      }
    }
  }
}

class HyperbusTest extends FlatSpec with ScalaFutures with Matchers with Eventually {
  implicit val mcx = new MessagingContext {
    override def createMessageId() = "123"
    override def correlationId = None
  }

  "ask " should "send a request (client)" in {
    val ct = new ClientTransportTest(
      """{"s":201,"t":"created-body","i":"123","l":{"a":"hb://test"}}""" + "\r\n" + """{"resourceId":"100500"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask testclasses.TestPost1(testclasses.TestBody1("ha ha")) runAsync

    ct.input should equal(
      """{"r":{"a":"hb://resources"},"m":"post","t":"test-1","i":"123"}""" + "\r\n" + """{"resourceData":"ha ha"}"""
    )

    f.futureValue.body should equal(testclasses.TestCreatedBody("100500"))
  }

  "ask " should "send a request, dynamic (client)" in {

    val ct = new ClientTransportTest(
      """{"s":201,"t":"created-body","i":"123"}""" + "\r\n" + """{"resourceId":"100500"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask DynamicRequest(HRI("hb://resources"),
      Method.POST,
      DynamicBody(
        Obj.from("resourceData" → "ha ha"),
        Some("test-1")
      )
    ) runAsync

    ct.input should equal(
      """{"r":{"a":"hb://resources"},"m":"post","t":"test-1","i":"123"}""" + "\r\n" + """{"resourceData":"ha ha"}"""
    )

    val r = f.futureValue
    r shouldBe a[Created[_]]
    r.body shouldBe a[DynamicBody]
  }

  "ask " should " receive empty response (client)" in {
    val ct = new ClientTransportTest(
      """{"s":204,"i":"123"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask TestPostWithNoContent(testclasses.TestBody1("empty")) runAsync

    ct.input should equal(
      """{"r":{"a":"hb://empty"},"m":"post","t":"test-1","i":"123"}""" + "\r\n" + """{"resourceData":"empty"}"""
    )

    val r = f.futureValue
    r shouldBe a[NoContent[_]]
    r.body shouldBe a[EmptyBody]
  }

    "ask " should " send static request with dynamic body (client)" in {
    val ct = new ClientTransportTest(
      """{"s":204,"i":"123"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask StaticPostWithDynamicBody(DynamicBody(Text("ha ha"))) runAsync

    ct.input should equal(
      """{"r":{"a":"hb://empty"},"m":"post","i":"123"}""" + "\r\n" + """"ha ha""""
    )

    val r = f.futureValue
    r shouldBe a[NoContent[_]]
    r.body shouldBe a[EmptyBody]
  }

  "ask " should " send static request with empty body (client)" in {
    val ct = new ClientTransportTest(
      """{"s":204,"i":"123"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask StaticPostWithEmptyBody() runAsync

    ct.input should equal(
      """{"r":{"a":"hb://empty"},"m":"post","i":"123"}"""
    )

    val r = f.futureValue
    r shouldBe a[NoContent[_]]
    r.body shouldBe a[EmptyBody]
  }

  "ask" should "send static request with body without contentType specified" in {
    val ct = new ClientTransportTest(
      """{"s":204,"i":"123"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask StaticPostBodyWithoutContentType(TestBodyNoContentType("yey")) runAsync

    ct.input should equal(
      """{"r":{"a":"hb://content-body-not-specified"},"m":"post","i":"123"}""" + "\r\n" + """{"resourceData":"yey"}"""
    )

    val r = f.futureValue
    r shouldBe a[NoContent[_]]
    r.body shouldBe a[EmptyBody]
  }

  "ask" should "send static request with some query (client)" in {
    val ct = new ClientTransportTest(
      """{"s":200,"i":"123"}""" + "\r\n" + """{"data":"abc"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask StaticGetWithQuery() runAsync

    ct.input should equal(
      """{"r":{"a":"hb://empty"},"m":"get","i":"123"}"""
    )

    val r = f.futureValue
    r shouldBe a[Ok[_]]
    r.body shouldBe a[DynamicBody]
  }

  "ask" should "catch client exception" in {
    val ct = new ClientTransportTest(
      """{"s":409,"i":"abcde12345"}""" + "\r\n" + """{"code":"failed","errorId":"abcde12345"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask testclasses.TestPost1(testclasses.TestBody1("ha ha")) runAsync

    ct.input should equal(
      """{"r":{"a":"hb://resources"},"m":"post","t":"test-1","i":"123"}""" + "\r\n" + """{"resourceData":"ha ha"}"""
    )

    val r = f.failed.futureValue
    r shouldBe a[Conflict[_]]
    r.asInstanceOf[Conflict[_]].body should equal(ErrorBody("failed", errorId = "abcde12345"))
  }

  "commands" should "handle server request" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[TestPost1].subscribe{ c ⇒
      c.responsePromise.success {
        Created(testclasses.TestCreatedBody("100500"))
      }
      Continue
    }

    val msg = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    val task = st.testCommand(msg)
    task.runAsync.futureValue should equal(Created(testclasses.TestCreatedBody("100500")))
  }

  "commands" should "handle server request when missed a contentType" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[TestPost1].subscribe{ c ⇒
      if (c.request.headers.contentType.isEmpty) {
        c.responsePromise.success {
          Created(testclasses.TestCreatedBody("100500"))
        }
      }
      else {
        c.responsePromise.failure(
          Conflict(ErrorBody("failed"))
        )
      }
      Continue
    }

    val msg = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    val msgWithoutContentType = msg.copy(
      headers = RequestHeaders(Obj(msg.headers.all.v.filterNot(_._1 == Header.CONTENT_TYPE)))
    )
    val task = st.testCommand(msgWithoutContentType)
    task.runAsync.futureValue should equal(Created(testclasses.TestCreatedBody("100500")))
  }

  "commands" should "handle server static request with empty body (server)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[StaticPostWithEmptyBody].subscribe{ c ⇒
      c.responsePromise.success {
        NoContent(EmptyBody)
      }
      Continue
    }

    val msg = StaticPostWithEmptyBody(EmptyBody)
    val task = st.testCommand(msg)
    task.runAsync.futureValue should equal(NoContent(EmptyBody))
  }

  "commands" should "call method for static request with dynamic body (server)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[StaticPostWithDynamicBody].subscribe{ post =>
      post.responsePromise.success {
        NoContent(EmptyBody)
      }
      Continue
    }

    val msg = StaticPostWithDynamicBody(DynamicBody("haha", Some("some-content")))
    val task = st.testCommand(msg)
    task.runAsync.futureValue should equal(NoContent(EmptyBody))
  }

  "commands" should "call method for dynamic request (server)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[DynamicRequest](
      DynamicRequest.requestMeta,
      DynamicRequestObservableMeta(RequestMatcher("/test", Method.GET, None))
    ).subscribe { post =>
      post.responsePromise.success {
        NoContent(EmptyBody)
      }
      Continue
    }

    val msg = DynamicRequest(
      HRI("/test"),
      Method.GET,
      DynamicBody("haha", Some("some-content")),
      Obj.from(
        Header.CONTENT_TYPE → "some-content",
        Header.MESSAGE_ID → "123"
      )
    )


    val task = st.testCommand(msg)
    task.runAsync.futureValue should equal(NoContent(EmptyBody))
  }

  "publish" should "publish static request (client)" in {
    val rsp = """{"status":409,"headers":{"messageId":"123"},"body":{"code":"failed","errorId":"abcde12345"}}"""
    var sentEvents = List[RequestBase]()
    val clientTransport = new ClientTransportTest(rsp) {
      override def publish(message: RequestBase): Task[PublishResult] = {
        Task.now {
          sentEvents = sentEvents :+ message
          new PublishResult {
            def sent = None

            def offset = None
          }
        }
      }
    }

    val hyperbus = newHyperbus(clientTransport, null)
    val futureResult = hyperbus.publish {
      testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    }.runAsync
    futureResult.futureValue
    sentEvents.size should equal(1)
  }

  "publish" should "publish dynamic request (client)" in {
    val rsp = """{"status":409,"headers":{"messageId":"123"},"body":{"code":"failed","errorId":"abcde12345"}}"""
    var sentEvents = List[RequestBase]()
    val clientTransport = new ClientTransportTest(rsp) {
      override def publish(message: RequestBase): Task[PublishResult] = {
        Task.now {
          sentEvents = sentEvents :+ message
          new PublishResult {
            def sent = None

            def offset = None
          }
        }
      }
    }

    val hyperbus = newHyperbus(clientTransport, null)
    val futureResult = hyperbus
      .publish(DynamicRequest(HRI("/resources"), Method.POST, DynamicBody(Obj.from("resourceData" → "ha ha"), Some("test-1"))))
      .runAsync

    futureResult.futureValue
    sentEvents.size should equal(1)
  }

  "events" should "receive event (server)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    @volatile var receivedEvents = 0
    hyperbus events[TestPost1] Some("group1") subscribe { post ⇒
      receivedEvents += 1
      Continue
    }

    val msg = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    val task = st.testEvent(msg)
    task.runAsync.futureValue shouldBe a[PublishResult]

    receivedEvents should equal(1)
  }

  "events" should "receive dynamic event (server)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    @volatile var receivedEvents = 0
    hyperbus
      .events(
        Some("group1"),
        DynamicRequestObservableMeta(RequestMatcher("hb://test", Method.POST))
      )
      .subscribe { post ⇒
        receivedEvents += 1
        Continue
      }

    val msg = DynamicRequest(HRI("hb://test"), Method.POST, EmptyBody)
    val task = st.testEvent(msg)
    task.runAsync.futureValue shouldBe a[PublishResult]
    eventually {
      receivedEvents should equal(1)
    }
  }

  "commands" should "handle exception thrown (correctly)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[TestPost1].subscribe{ c ⇒
      c.responsePromise.failure {
        Conflict(ErrorBody("failed", errorId = "abcde12345"))
      }
      Continue
    }

    val msg = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    val task = st.testCommand(msg)
    val r = task.runAsync.failed.futureValue
    r shouldBe a[Conflict[_]]
    r.asInstanceOf[Conflict[_]].body should equal(ErrorBody("failed", errorId = "abcde12345"))
  }

  /* TODO: this doesn't work
  "commands" should "handle exception thrown (incorrectly)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[TestPost1].subscribe{ c ⇒
      throw Conflict(ErrorBody("failed", errorId = "abcde12345"))
    }

    val msg = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    val task = st.testCommand(msg)
    val r = task.runAsync.failed.futureValue
    r shouldBe a[Conflict[_]]
    r.asInstanceOf[Conflict[_]].body should equal(ErrorBody("failed", errorId = "abcde12345"))
  }
  */

  "cancel" should "disable commands subscription" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    val id1f = hyperbus
      .commands[TestPost1]
      .subscribe { c ⇒
      c.responsePromise.failure {
        Conflict(ErrorBody("failed", errorId = "abcde12345"))
      }
      Continue
    }

    st.sCommandsSubject shouldNot equal(null)
    id1f.cancel()
    st.sCommandsSubject should equal(null)

    val id2f = hyperbus.commands[TestPost1].subscribe{ c ⇒
      c.responsePromise.failure {
        Conflict(ErrorBody("failed", errorId = "abcde12345"))
      }
      Continue
    }

    st.sCommandsSubject shouldNot equal(null)
    id2f.cancel()
    st.sCommandsSubject should equal(null)
  }

  "cancel" should " disable events subscription" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)

    val id1f = hyperbus events[TestPost1] Some("group1") subscribe { post ⇒
      Continue
    }

    st.sEventsSubject shouldNot equal(null)
    id1f.cancel()
    st.sEventsSubject should equal(null)

    val id2f = hyperbus events[TestPost1] Some("group1") subscribe { post ⇒
      Continue
    }

    st.sEventsSubject shouldNot equal(null)
    id2f.cancel()
    st.sEventsSubject should equal(null)
  }

  def newHyperbus(ct: ClientTransport, st: ServerTransport) = {
    implicit val injector = new Module {
      bind [Scheduler] to global
    }
    val cr = List(TransportRoute(ct, RequestMatcher.any))
    val sr = List(TransportRoute(st, RequestMatcher.any))
    val transportManager = new TransportManager(cr, sr, global, injector)
    new Hyperbus(transportManager, Some("group1"), logMessages = true)
  }
}
