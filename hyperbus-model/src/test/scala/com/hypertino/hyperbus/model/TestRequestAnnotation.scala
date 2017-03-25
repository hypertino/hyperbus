package com.hypertino.hyperbus.model

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, Reader}

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.{Obj, _}
import com.hypertino.hyperbus.model.annotations.{body, request}
import com.hypertino.hyperbus.serialization.MessageReader
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import org.scalatest.{FlatSpec, Matchers}

@request(Method.POST, "hb://test")
case class TestPost1(id: String, body: TestBody1) extends Request[TestBody1]

trait TestPost1ObjectApi {
  def apply(id: String, x: TestBody1, headers: Obj)(implicit mcx: MessagingContext): TestPost1
}

object TestPost1 extends RequestMetaCompanion[TestPost1] with TestPost1ObjectApi {
  def apply(id: String, x: String, headers: Obj)(implicit mcx: MessagingContext): TestPost1 = TestPost1(id, TestBody1(x), headers)(mcx)
}

@request(Method.GET, "hb://test")
case class TestGet1(id: String, body: EmptyBody) extends Request[EmptyBody]

@request(Method.POST, "hb://test")
case class TestPost1DefinedResponse(id: String, body: TestBody1)
  extends Request[TestBody1]
    with DefinedResponse[Ok[TestBody2]]

@request(Method.POST, "hb://test")
case class TestPost1MultipleDefinedResponse(id: String, body: TestBody1)
  extends Request[TestBody1]
    with DefinedResponse[(Ok[TestBody2], Ok[TestBody3])]


//
//@body("test-inner-body")
//case class TestInnerBody(innerData: String) extends Body {
//  def toEmbedded(links: Links = Links(HRI("hb://test-inner-resource"))) = TestInnerBodyEmbedded(innerData, links)
//}
//
//object TestInnerBody extends BodyObjectApi[TestInnerBody]
//
//@body("test-inner-body")
//case class TestInnerBodyEmbedded(innerData: String) extends Body {
//  def toOuter: TestInnerBody = TestInnerBody(innerData)
//}
//
//object TestInnerBodyEmbedded extends BodyObjectApi[TestInnerBodyEmbedded]
//
//case class TestOuterBodyEmbedded(simple: TestInnerBodyEmbedded, collection: List[TestInnerBodyEmbedded])
//
//@body("test-outer-body")
//case class TestOuterBody(outerData: String,
//                         @fieldName("_embedded") embedded: TestOuterBodyEmbedded) extends Body
//
//object TestOuterBody extends BodyObjectApi[TestOuterBody]
//
//
//@request(Method.GET, "hb://test-outer-resource")
//case class TestOuterResource(body: TestOuterBody) extends Request[TestOuterBody]
//
//object TestOuterResource extends RequestObjectApi[TestOuterResource]

class TestRequestAnnotation extends FlatSpec with Matchers {
  implicit val mcx = new MessagingContext {
    override def createMessageId() = "123"
    override def correlationId = None
  }
  val rn = "\r\n"

  "TestPost1" should "serialize" in {
    val post1 = TestPost1("155", TestBody1("abcde"))
    post1.serializeToString should equal(
      s"""{"r":{"q":{"id":"155"},"a":"hb://test"},"m":"post","t":"test-body-1","i":"123"}""" + rn +
        """{"data":"abcde"}""")
  }

  "TestPost1DefinedResponse" should "serialize" in {
    val post1 = TestPost1DefinedResponse("155", TestBody1("abcde"))
    post1.serializeToString should equal(
      s"""{"r":{"q":{"id":"155"},"a":"hb://test"},"m":"post","t":"test-body-1","i":"123"}""" + rn +
        """{"data":"abcde"}""")
  }

  "TestPost1DefinedResponse" should " have meta" in {
    val requestMeta = implicitly[RequestMeta[TestPost1DefinedResponse]]
    val observableMeta = implicitly[RequestObservableMeta[TestPost1DefinedResponse]]

    observableMeta.requestMatcher should equal(RequestMatcher("hb://test", "post", Some("test-body-1")))

    val s = """{"s":200,"t":"test-body-2","i":"123","l":{"a":"hb://test"}}""" + rn +
      """{"x":"100500","y":555}"""

    val response: requestMeta.ResponseType = MessageReader.from(s, requestMeta.responseDeserializer)
    response.body should equal (TestBody2("100500",555))
    response shouldBe a[Ok[_]]
  }

  "TestPost1MultipleDefinedResponse" should " have meta" in {
    val requestMeta = implicitly[RequestMeta[TestPost1MultipleDefinedResponse]]
    val observableMeta = implicitly[RequestObservableMeta[TestPost1MultipleDefinedResponse]]

    observableMeta.requestMatcher should equal(RequestMatcher("hb://test", "post", Some("test-body-1")))

    val s1 = """{"s":200,"t":"test-body-2","i":"123","l":{"a":"hb://test"}}""" + rn +
      """{"x":"100500","y":555}"""

    val response1: requestMeta.ResponseType = MessageReader.from(s1, requestMeta.responseDeserializer)
    response1.body should equal (TestBody2("100500",555))
    response1 shouldBe a[Ok[_]]

    val s2 = """{"s":200,"t":"test-body-3","i":"123","l":{"a":"hb://test"}}""" + rn +
      """{"x":"100500","y":555, "z": 888}"""

    val response2: requestMeta.ResponseType = MessageReader.from(s2, requestMeta.responseDeserializer)
    response2.body should equal (TestBody3("100500",555, 888l))
    response2 shouldBe a[Ok[_]]
  }

  "TestGet1 (with EmptyBody)" should "serialize" in {
    val r = TestGet1("155", EmptyBody)
    r.serializeToString should equal(
      s"""{"r":{"q":{"id":"155"},"a":"hb://test"},"m":"get","i":"123"}""")
  }

  "TestGet1 (with EmptyBody)" should "deserialize" in {
    val str = s"""{"r":{"q":{"id":"155"},"a":"hb://test"},"m":"get","i":"123"}"""
    TestGet1.from(str) should equal (TestGet1("155", EmptyBody))
  }

  "TestPost1" should "serialize with headers" in {
    val post1 = TestPost1("155", TestBody1("abcde"), Obj.from("test" → Lst.from("a")))
    post1.serializeToString should equal(
      s"""{"r":{"q":{"id":"155"},"a":"hb://test"},"m":"post","t":"test-body-1","i":"123","test":["a"]}""" + rn +
         """{"data":"abcde"}""")
  }

  "TestPost1" should "deserialize" in {
    val str = """{"r":{"q":{"id":"155"},"a":"hb://test-post-1"},"m":"post","t":"test-body-1","i":"123"}""" + rn +
      """{"data":"abcde"}"""
    val post = TestPost1.from(str)
    post.headers.hri should equal(HRI("hb://test-post-1", Obj.from("id" -> "155")))
    post.headers.contentType should equal(Some("test-body-1"))
    post.headers.method should equal("post")
    post.headers.messageId should equal("123")
    post.headers.correlationId should equal(Some("123"))

    post.body should equal(TestBody1("abcde"))
    post.id should equal("155")
  }

  /*
  todo: embedded and links!

  "TestOuterPost" should "serialize" in {
    val ba = new ByteArrayOutputStream()
    val inner1 = TestInnerBodyEmbedded("eklmn")
    val inner2 = TestInnerBodyEmbedded("xyz")
    val inner3 = TestInnerBodyEmbedded("yey")
    val postO = TestOuterResource(TestOuterBody("abcde",
      TestOuterBodyEmbedded(inner1, List(inner2, inner3))
    ))
    val str = postO.serializeToString
    str should equal("""{"r":{"a":"hb://test-outer-resource"},"m":"get","t":"test-outer-body","i":"123"}""" + rn +
      """{"outerData":"abcde","_embedded":{"simple":{"innerData":"eklmn","_links":{"self":{"r":{"a":"hb://test-inner-resource"}}}},"collection":[{"innerData":"xyz","_links":{"self":{"r":{"a":"hb://test-inner-resource"}}}},{"innerData":"yey","_links":{"self":{"r":{"a":"hb://test-inner-resource"}}}}]}}""")
  }

  "TestOuterPost" should "deserialize" in {
    val str = """{"r":{"a":"hb://test-outer-resource"},"m":"get","t":"test-outer-body","i":"123"}""" + rn +
      """{"outerData":"abcde","_embedded":{"simple":{"innerData":"eklmn","_links":{"self":{"r":{"a":"hb://test-inner-resource"}}}},"collection":[{"innerData":"xyz","_links":{"self":{"r":{"a":"hb://test-inner-resource"}}}},{"innerData":"yey","_links":{"self":{"r":{"a":"hb://test-inner-resource"}}}}]}}"""

    val outer = TestOuterResource(str)

    val inner1 = TestInnerBodyEmbedded("eklmn")
    val inner2 = TestInnerBodyEmbedded("xyz")
    val inner3 = TestInnerBodyEmbedded("yey")
    val outerBody = TestOuterBody("abcde",
      TestOuterBodyEmbedded(inner1, List(inner2, inner3))
    )

    outer.body should equal(outerBody)
    outer.headers.hri should equal(HRI("hb://test-outer-resource"))
  }
  */

  "DynamicRequest" should "decode" in {
    val str = """{"r":{"a":"hb://test-outer-resource"},"m":"custom-method","t":"test-body-1","i":"123"}""" + rn +
      """{"resourceId":"100500"}"""
    val request = DynamicRequest.from(str)
    request shouldBe a[Request[_]]
    request.headers.method should equal("custom-method")
    request.headers.hri should equal(HRI("hb://test-outer-resource"))
    request.headers.messageId should equal("123")
    request.correlationId should equal(Some("123"))
    request.body should equal(DynamicBody(Obj.from("resourceId" -> "100500"), Some("test-body-1")))
  }

  "hashCode, equals, product" should "work" in {
    val post1 = TestPost1("155", TestBody1("abcde"))
    val post2 = TestPost1("155", TestBody1("abcde"))
    val post3 = TestPost1("155", TestBody1("abcdef"))
    post1 should equal(post2)
    post1.hashCode() should equal(post2.hashCode())
    post1 shouldNot equal(post3)
    post1.hashCode() shouldNot equal(post3.hashCode())
    post1.productElement(0) should equal("155")
    post1.productElement(1) should equal(TestBody1("abcde"))
    post1.productElement(2) shouldBe a[RequestHeaders]
  }
}

