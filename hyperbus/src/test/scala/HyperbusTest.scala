/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

import java.util.concurrent.atomic.AtomicLong

import com.hypertino.binders.value.{Obj, Text}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.subscribe.Subscribable
import com.hypertino.hyperbus.subscribe.annotations.groupName
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.transport.registrators.DummyRegistrator
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.Scheduler.Implicits.global
import monix.execution.atomic.AtomicInt
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import monix.reactive.subjects.ConcurrentSubject
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FlatSpec, Matchers}
import scaldi.Module
import testclasses.{TestPost1, _}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

class ClientTransportTest(output: String) extends ClientTransport {
  private val messageBuf = new StringBuilder

  def input = messageBuf.toString()

  override def ask(message: RequestBase, responseDeserializer: ResponseBaseDeserializer): Task[ResponseBase] = {
    messageBuf.append(message.serializeToString)
    Task.fromTry(Try{MessageReader.fromString(output, responseDeserializer)})
  }

  override def publish(message: RequestBase): Task[PublishResult] = {
    ask(message, null) map { x =>
      PublishResult.empty
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
    val observable = new Observable[CommandEvent[RequestBase]] {
      override def unsafeSubscribeFn(subscriber: Subscriber[CommandEvent[RequestBase]]): Cancelable = {
        val original: Cancelable = sCommandsSubject.unsafeSubscribeFn(subscriber)
        new Cancelable {
          override def cancel(): Unit = {
            sCommandsSubject = null
            original.cancel()
          }
        }
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
    val observable: Observable[RequestBase] = new Observable[RequestBase] {
      override def unsafeSubscribeFn(subscriber: Subscriber[RequestBase]): Cancelable = {
        val original: Cancelable = sEventsSubject.unsafeSubscribeFn(subscriber)
        new Cancelable {
          override def cancel(): Unit = {
            sEventsSubject = null
            original.cancel()
          }
        }
      }
    }

    observable.asInstanceOf[Observable[REQ]]
  }

  override def shutdown(duration: FiniteDuration): Task[Boolean] = {
    Task.now(true)
  }

  def testCommand(msg: RequestBase): Task[ResponseBase] = {
    Task.create[ResponseBase]{ (_, callback) ⇒
      val command = CommandEvent(msg, callback)
      sCommandsSubject.onNext(command)
      Cancelable.empty
    }
  }

  def testEvent(msg: RequestBase): Task[PublishResult] = {
    sEventsSubject.onNext(msg)
    Task.now {
      PublishResult.committed
    }
  }
}

class TestServiceClass(hyperbus: Hyperbus) extends Subscribable {
  val subscriptions = hyperbus.subscribe(this)
  val okEvents = AtomicInt(0)
  val failedEvents = AtomicInt(0)

  def onTestPost1Command(post1: TestPost1) = Task.eval {
    implicit val mcx = new MessagingContext {
      override def createMessageId() = "123"
      override def correlationId: String = post1.correlationId
      override def parentId: Option[String] = post1.parentId
    }

    if (post1.headers.hrl.query.dynamic.ok.isDefined) {
      Created(testclasses.TestCreatedBody("100500"))
    }
    else {
      throw Conflict(ErrorBody("failed"))
    }
  }

  def onTestPost1Event(post1: TestPost1): Ack = {
    if (post1.headers.hrl.query.dynamic.ok.isDefined) {
      okEvents.incrementAndGet()
    }
    else {
      failedEvents.incrementAndGet()
    }
    Continue
  }

  def stop() = {
    subscriptions.foreach(_.cancel)
  }
}

class TestServiceClass2(hyperbus: Hyperbus) extends Subscribable {
  val subscriptions = hyperbus.subscribe(this)

  @groupName("group2")
  def onTestPost1Event2(post1: TestPost1): Future[Ack] = {
    Continue
  }

  def stop() = {
    subscriptions.foreach(_.cancel)
  }

  override def groupName(existing: Option[String]): Option[String] = existing.map(_ + "-test")
}

class HyperbusTest extends FlatSpec with ScalaFutures with Matchers with Eventually {
  implicit val mcx = new MessagingContext {
    override def createMessageId() = "123"
    override def correlationId = "123"
    override def parentId: Option[String] = Some("123")
  }

  "ask " should "send a request (client)" in {
    val ct = new ClientTransportTest(
      """{"s":201,"t":"created-body","i":"123","r":{"l":"hb://test"}}""" + "\r\n" + """{"resource_id":"100500"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask testclasses.TestPost1(testclasses.TestBody1("ha ha")) runAsync

    ct.input should equal(
      """{"r":{"l":"hb://resources"},"m":"post","t":"application/vnd.test-1+json","i":"123"}""" + "\r\n" + """{"resource_data":"ha ha"}"""
    )

    f.futureValue.body should equal(testclasses.TestCreatedBody("100500"))
    f.futureValue.body.resourceId should equal("100500")
  }

  it should "send a request (client) to the transport that has service" in {
    val ct1 = new ClientTransportTest(
      """{"s":201,"t":"created-body","i":"123","r":{"l":"hb://fail"}}""" + "\r\n" + """{"resource_id":"fail"}"""
    )
    val ct2 = new ClientTransportTest(
      """{"s":201,"t":"created-body","i":"123","r":{"l":"hb://test"}}""" + "\r\n" + """{"resource_id":"100500"}"""
    )

    val cr: Seq[ClientTransportRoute] = Seq(
      ClientTransportRoute(ct1, RequestMatcher("hb://not-matches")),
      ClientTransportRoute(ct2, RequestMatcher.any)
    )
    val sr = Seq.empty
    val hyperbus = hb(cr, sr)

    val f = hyperbus ask testclasses.TestPost1(testclasses.TestBody1("ha ha")) runAsync

    f.futureValue.body should equal(testclasses.TestCreatedBody("100500"))
    f.futureValue.body.resourceId should equal("100500")

    ct2.input should equal(
      """{"r":{"l":"hb://resources"},"m":"post","t":"application/vnd.test-1+json","i":"123"}""" + "\r\n" + """{"resource_data":"ha ha"}"""
    )
  }

  it should "send a request, dynamic (client)" in {

    val ct = new ClientTransportTest(
      """{"s":201,"t":"created-body","i":"123"}""" + "\r\n" + """{"resource_id":"100500"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask DynamicRequest(HRL("hb://resources"),
      Method.POST,
      DynamicBody(
        Obj.from("resource_data" → "ha ha"),
        Some("test-1")
      )
    ) runAsync

    ct.input should equal(
      """{"r":{"l":"hb://resources"},"m":"post","t":"application/vnd.test-1+json","i":"123"}""" + "\r\n" + """{"resource_data":"ha ha"}"""
    )

    val r = f.futureValue
    r shouldBe a[Created[_]]
    r.body shouldBe a[DynamicBody]
  }

  it should " receive empty response (client)" in {
    val ct = new ClientTransportTest(
      """{"s":204,"i":"123"}""" + "\r\n" + "{}"
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask TestPostWithNoContent(testclasses.TestBody1("empty")) runAsync

    ct.input should equal(
      """{"r":{"l":"hb://empty"},"m":"post","t":"application/vnd.test-1+json","i":"123"}""" + "\r\n" + """{"resource_data":"empty"}"""
    )

    val r = f.futureValue
    r shouldBe a[NoContent[_]]
    r.body shouldBe a[EmptyBody]
  }

  it should " send static request with dynamic body (client)" in {
    val ct = new ClientTransportTest(
      """{"s":204,"i":"123"}""" + "\r\n" + "{}"
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask StaticPostWithDynamicBody(DynamicBody(Text("ha ha"))) runAsync

    ct.input should equal(
      """{"r":{"l":"hb://empty"},"m":"post","i":"123"}""" + "\r\n" + """"ha ha""""
    )

    val r = f.futureValue
    r shouldBe a[NoContent[_]]
    r.body shouldBe a[EmptyBody]
  }

  it should " send static request with empty body (client)" in {
    val ct = new ClientTransportTest(
      """{"s":204,"i":"123"}""" + "\r\n" + "{}"
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask StaticPostWithEmptyBody() runAsync

    ct.input should equal(
      """{"r":{"l":"hb://empty"},"m":"post","i":"123"}""" + "\r\n" + "{}"
    )

    val r = f.futureValue
    r shouldBe a[NoContent[_]]
    r.body shouldBe a[EmptyBody]
  }

  it should "send static request with body without contentType specified" in {
    val ct = new ClientTransportTest(
      """{"s":204,"i":"123"}""" + "\r\n" + "{}"
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask StaticPostBodyWithoutContentType(TestBodyNoContentType("yey")) runAsync

    ct.input should equal(
      """{"r":{"l":"hb://content-body-not-specified"},"m":"post","i":"123"}""" + "\r\n" + """{"resource_data":"yey"}"""
    )

    val r = f.futureValue
    r shouldBe a[NoContent[_]]
    r.body shouldBe a[EmptyBody]
  }

  it should "send static request with some query (client)" in {
    val ct = new ClientTransportTest(
      """{"s":200,"i":"123"}""" + "\r\n" + """{"data":"abc"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask StaticGetWithQuery() runAsync

    ct.input should equal(
      """{"r":{"l":"hb://empty"},"m":"get","i":"123"}""" + "\r\n" + "{}"
    )

    val r = f.futureValue
    r shouldBe a[Ok[_]]
    r.body shouldBe a[DynamicBody]
  }

  it should "send static request with some query with optional params (client)" in {
    val ct = new ClientTransportTest(
      """{"s":200,"i":"123"}""" + "\r\n" + """{"resource_id":"100500"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask StaticGetWithQueryAndOptionalParams(x = Some("abc")) runAsync

    ct.input should equal(
      """{"r":{"q":{"x":"abc"},"l":"hb://test-optional-query-params"},"m":"get","i":"123"}""" + "\r\n" + "{}"
    )

    val r = f.futureValue
    r shouldBe a[Ok[_]]
    r.body shouldBe a[TestAnotherBody]
  }

  it should "catch client exception" in {
    val ct = new ClientTransportTest(
      """{"s":409,"i":"abcde12345"}""" + "\r\n" + """{"code":"failed","error_id":"abcde12345"}"""
    )

    val hyperbus = newHyperbus(ct, null)
    val f = hyperbus ask testclasses.TestPost1(testclasses.TestBody1("ha ha")) runAsync

    ct.input should equal(
      """{"r":{"l":"hb://resources"},"m":"post","t":"application/vnd.test-1+json","i":"123"}""" + "\r\n" + """{"resource_data":"ha ha"}"""
    )

    val r = f.failed.futureValue
    r shouldBe a[Conflict[_]]
    r.asInstanceOf[Conflict[_]].body should equal(ErrorBody("failed", errorId = "abcde12345"))
  }

  "commands" should "handle server request" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[TestPost1].subscribe{ c ⇒
      c.reply(Success(
        Created(testclasses.TestCreatedBody("100500"))
      ))
      Continue
    }

    val msg = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    val task = st.testCommand(msg)
    task.runAsync.futureValue should equal(Created(testclasses.TestCreatedBody("100500")))
  }

  it should "handle server request from multiple transports" in {
    val st1 = new ServerTransportTest()
    val st2 = new ServerTransportTest()

    val cr = Seq.empty
    val sr: Seq[ServerTransportRoute] = Seq(
      ServerTransportRoute(st1, RequestMatcher.any, DummyRegistrator),
      ServerTransportRoute(st2, RequestMatcher.any, DummyRegistrator)
    )
    val hyperbus = hb(cr, sr)

    val subscriptions = hyperbus.commands[TestPost1].subscribe{ c ⇒
      c.reply(Success(
        Created(testclasses.TestCreatedBody("100500"))
      ))
      Continue
    }

    val msg = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    val task1 = st1.testCommand(msg)
    val task2 = st2.testCommand(msg)
    task1.runAsync.futureValue should equal(Created(testclasses.TestCreatedBody("100500")))
    task2.runAsync.futureValue should equal(Created(testclasses.TestCreatedBody("100500")))

    subscriptions.cancel()
  }

  it should "handle server request when missed a contentType" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[TestPost1].subscribe{ c ⇒
      c.reply(Try{
        if (c.request.headers.contentType.isEmpty) {
          Created(testclasses.TestCreatedBody("100500"))
        }
        else {
          throw Conflict(ErrorBody("failed"))
        }
      })
      Continue
    }

    val msg = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    val headers = msg.headers.filterNot(_._1 == Header.CONTENT_TYPE)
    val msgWithoutContentType = msg.copy(
      headers = headers
    )
    val task = st.testCommand(msgWithoutContentType)
    task.runAsync.futureValue should equal(Created(testclasses.TestCreatedBody("100500")))
  }

  it should "handle server static request with empty body (server)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[StaticPostWithEmptyBody].subscribe{ c ⇒
      c.reply(Success {
        NoContent(EmptyBody)
      })
      Continue
    }

    val msg = StaticPostWithEmptyBody(EmptyBody)
    val task = st.testCommand(msg)
    task.runAsync.futureValue should equal(NoContent(EmptyBody))
  }

  it should "call method for static request with dynamic body (server)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[StaticPostWithDynamicBody].subscribe{ post =>
      post.reply(Success {
        NoContent(EmptyBody)
      })
      Continue
    }

    val msg = StaticPostWithDynamicBody(DynamicBody("haha", Some("some-content")))
    val task = st.testCommand(msg)
    task.runAsync.futureValue should equal(NoContent(EmptyBody))
  }

  it should "call method for dynamic request (server)" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    hyperbus.commands[DynamicRequest](
      DynamicRequest.requestMeta,
      DynamicRequestObservableMeta(RequestMatcher("/test", Method.GET, None))
    ).subscribe { post =>
      post.reply(Success {
        NoContent(EmptyBody)
      })
      Continue
    }

    val msg = DynamicRequest(
      HRL("/test"),
      Method.GET,
      DynamicBody("haha", Some("some-content")),
      Headers(
        Header.CONTENT_TYPE → "some-content",
        Header.MESSAGE_ID → "123"
      )
    )


    val task = st.testCommand(msg)
    task.runAsync.futureValue should equal(NoContent(EmptyBody))
  }

  "publish" should "publish static request (client)" in {
    val rsp = """{"status":409,"headers":{"message_id":"123"},"body":{"code":"failed","error_id":"abcde12345"}}"""
    var sentEvents = List[RequestBase]()
    val clientTransport = new ClientTransportTest(rsp) {
      override def publish(message: RequestBase): Task[PublishResult] = {
        Task.now {
          sentEvents = sentEvents :+ message
          PublishResult.empty
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

  it should "publish to every available transport" in {
    val rsp = """{"status":409,"headers":{"message_id":"123"},"body":{"code":"failed","error_id":"abcde12345"}}"""
    var sentEvents1 = List[RequestBase]()
    val ct1 = new ClientTransportTest(rsp) {
      override def publish(message: RequestBase): Task[PublishResult] = {
        Task.now {
          sentEvents1 = sentEvents1 :+ message
          PublishResult.empty
        }
      }
    }

    var sentEvents2 = List[RequestBase]()
    val ct2 = new ClientTransportTest(rsp) {
      override def publish(message: RequestBase): Task[PublishResult] = {
        Task.now {
          sentEvents2 = sentEvents2 :+ message
          PublishResult.empty
        }
      }
    }

    val cr: Seq[ClientTransportRoute] = Seq(
      ClientTransportRoute(ct1, RequestMatcher.any),
      ClientTransportRoute(ct2, RequestMatcher.any)
    )
    val sr = Seq.empty
    val hyperbus = hb(cr, sr)

    val futureResult = hyperbus.publish {
      testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    }.runAsync
    futureResult.futureValue shouldBe a[Seq[_]]
    sentEvents1.size should equal(1)
    sentEvents2.size should equal(1)
  }

  it should "publish dynamic request (client)" in {
    val rsp = """{"status":409,"headers":{"message_id":"123"},"body":{"code":"failed","error_id":"abcde12345"}}"""
    var sentEvents = List[RequestBase]()
    val clientTransport = new ClientTransportTest(rsp) {
      override def publish(message: RequestBase): Task[PublishResult] = {
        Task.now {
          sentEvents = sentEvents :+ message
          PublishResult.empty
        }
      }
    }

    val hyperbus = newHyperbus(clientTransport, null)
    val futureResult = hyperbus
      .publish(DynamicRequest(HRL("/resources"), Method.POST, DynamicBody(Obj.from("resource_data" → "ha ha"), Some("test-1"))))
      .runAsync

    futureResult.futureValue
    eventually {
      sentEvents.size should equal(1)
    }
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

    eventually {
      receivedEvents should equal(1)
    }
  }

  it should "receive event (server) from multiple transports" in {
    val st1 = new ServerTransportTest()
    val st2 = new ServerTransportTest()

    val cr = Seq.empty
    val sr: Seq[ServerTransportRoute] = Seq(
      ServerTransportRoute(st1, RequestMatcher.any, DummyRegistrator),
      ServerTransportRoute(st2, RequestMatcher.any, DummyRegistrator)
    )
    val hyperbus = hb(cr, sr)

    val receivedEvents = AtomicInt(0)
    val subscriptions = hyperbus events[TestPost1] Some("group1") subscribe { post ⇒
      receivedEvents.increment()
      Continue
    }

    val msg1 = testclasses.TestPost1(testclasses.TestBody1("ha ha"))
    val task1 = st1.testEvent(msg1)
    task1.runAsync.futureValue shouldBe a[PublishResult]

    val msg2 = testclasses.TestPost1(testclasses.TestBody1("sad"))
    val task2 = st2.testEvent(msg2)
    task2.runAsync.futureValue shouldBe a[PublishResult]

    eventually {
      receivedEvents.get shouldBe 2
    }
    subscriptions.cancel()
  }

  it should "receive dynamic event (server)" in {
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

    val msg = DynamicRequest(HRL("hb://test"), Method.POST, EmptyBody)
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
      c.reply(Failure {
        Conflict(ErrorBody("failed", errorId = "abcde12345"))
      })
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
        c.reply(Failure {
          Conflict(ErrorBody("failed", errorId = "abcde12345"))
        })
      Continue
    }

    st.sCommandsSubject shouldNot equal(null)
    id1f.cancel()
    st.sCommandsSubject should equal(null)

    val id2f = hyperbus.commands[TestPost1].subscribe{ c ⇒
      c.reply(Failure {
        Conflict(ErrorBody("failed", errorId = "abcde12345"))
      })
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

  "this" should "compile to ensure that we use covariance" in {
    val y: CommandEvent[TestPost1] = null
    val x: CommandEvent[RequestBase] = y
    x shouldBe null
  }

  "this" should "just compile, not to run" ignore {
    val y: CommandEvent[TestPost1] = null
    val t: Task[ResponseBase] = null
    t.runOnComplete(y.reply)
  }

  it should "subscribe to commands and events" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    val ts = new TestServiceClass(hyperbus)

    st.sGroupName shouldBe "group1"
    val msg = testclasses.TestPost1(testclasses.TestBody1("ha ha"), query=Obj.from("ok" → true))
    val task = st.testCommand(msg)
    task.runAsync.futureValue should equal(Created(testclasses.TestCreatedBody("100500")))

    ts.okEvents.get shouldBe 0
    val eventTask = st.testEvent(msg)
    eventTask.runAsync.futureValue
    eventually {
      ts.okEvents.get shouldBe 1
    }
    ts.stop()
  }

  it should "use group if defined in annotation" in {
    val st = new ServerTransportTest()
    val hyperbus = newHyperbus(null, st)
    val ts = new TestServiceClass2(hyperbus)
    st.sGroupName shouldBe "group2-test"
    ts.stop()
  }

  def newHyperbus(ct: ClientTransport, st: ServerTransport) = {
    val cr = List(ClientTransportRoute(ct, RequestMatcher.any))
    val sr = List(ServerTransportRoute(st, RequestMatcher.any, DummyRegistrator))
    hb(cr,sr)
  }

  def hb(cr: Seq[ClientTransportRoute], sr: Seq[ServerTransportRoute]) = {
    implicit val injector = new Module {
      bind [Scheduler] to global
    }
    new Hyperbus(Some("group1"),
      readMessagesLogLevel = "TRACE", writeMessagesLogLevel = "DEBUG",
      serverReadMessagesLogLevel = "TRACE", serverWriteMessagesLogLevel = "DEBUG",
      cr, sr, global, injector)
  }
}
