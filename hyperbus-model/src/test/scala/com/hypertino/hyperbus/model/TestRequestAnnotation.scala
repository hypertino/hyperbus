package com.hypertino.hyperbus.model

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.{Obj, _}
import com.hypertino.hyperbus.model.annotations.{body, request}
import com.hypertino.hyperbus.serialization.MessageReader
import org.scalatest.{FlatSpec, Matchers}

@request(Method.POST, "hb://test")
case class TestPost1(id: String, body: TestBody1) extends Request[TestBody1]

trait TestPost1ObjectApi {
  def apply(id: String, x: TestBody1, headers: Obj)(implicit mcx: MessagingContext): TestPost1
}

object TestPost1 extends RequestObjectApi[TestPost1] with TestPost1ObjectApi {
  def apply(id: String, x: String, headers: Obj)(implicit mcx: MessagingContext): TestPost1 = TestPost1(id, TestBody1(x), headers)(mcx)
}

@body("test-inner-body")
case class TestInnerBody(innerData: String) extends Body {
  def toEmbedded(links: Links = Links(HRI("hb://test-inner-resource"))) = TestInnerBodyEmbedded(innerData, links)
}

object TestInnerBody extends BodyObjectApi[TestInnerBody]

@body("test-inner-body")
case class TestInnerBodyEmbedded(innerData: String,
                                 @fieldName("_links") links: Links = Links(HRI("hb://test-inner-resource"))) extends Body with HalLinks {

  def toOuter: TestInnerBody = TestInnerBody(innerData)
}

object TestInnerBodyEmbedded extends BodyObjectApi[TestInnerBodyEmbedded]

case class TestOuterBodyEmbedded(simple: TestInnerBodyEmbedded, collection: List[TestInnerBodyEmbedded])

@body("test-outer-body")
case class TestOuterBody(outerData: String,
                         @fieldName("_embedded") embedded: TestOuterBodyEmbedded) extends Body

object TestOuterBody extends BodyObjectApi[TestOuterBody]

@request(Method.GET, "hb://test-outer-resource")
case class TestOuterResource(body: TestOuterBody) extends Request[TestOuterBody]

object TestOuterResource extends RequestObjectApi[TestOuterResource]

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

  "TestPost1" should "serialize with headers" in {
    val post1 = TestPost1("155", TestBody1("abcde"), Obj.from("test" → Lst.from("a")))
    post1.serializeToString should equal(
      s"""{"r":{"q":{"id":"155"},"a":"hb://test"},"m":"post","t":"test-body-1","i":"123","test":["a"]}""" + rn +
         """{"data":"abcde"}""")
  }

  "TestPost1" should "deserialize" in {
    val str = """{"r":{"q":{"id":"155"},"a":"hb://test-post-1"},"m":"post","t":"test-body-1","i":"123"}""" + rn +
      """{"data":"abcde"}"""
    val post = TestPost1(str)
    post.headers.hri should equal(HRI("hb://test-post-1", Obj.from("id" -> "155")))
    post.headers.contentType should equal(Some("test-body-1"))
    post.headers.method should equal("post")
    post.headers.messageId should equal("123")
    post.headers.correlationId should equal(Some("123"))

    post.body should equal(TestBody1("abcde"))
    post.id should equal("155")
  }

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

  "DynamicRequest" should "decode" in {
    val str = """{"r":{"a":"hb://test-outer-resource"},"m":"custom-method","t":"test-body-1","i":"123"}""" + rn +
      """{"resourceId":"100500"}"""
    val request = DynamicRequest(str)
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

