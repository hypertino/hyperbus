/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model

import java.io.Writer

import com.hypertino.binders.core.BindOptions
import com.hypertino.binders.json.{DefaultJsonBindersFactory, JsonBindersFactory}
import com.hypertino.binders.value.{Obj, _}
import com.hypertino.hyperbus.model.annotations.{body, request}
import com.hypertino.hyperbus.serialization.{MessageReader, SerializationOptions}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.inflector.naming.{CamelCaseToSnakeCaseConverter, PlainConverter}
import org.scalatest.{FlatSpec, Matchers}

@request(Method.POST, "hb://test")
case class TestPost1(id: String, body: TestBody1) extends Request[TestBody1]

trait TestPost1ObjectApi {
  def apply(id: String, body: TestBody1,
            headers: com.hypertino.hyperbus.model.Headers = com.hypertino.hyperbus.model.Headers.empty,
            query: com.hypertino.binders.value.Value = com.hypertino.binders.value.Null
           )(implicit mcx: MessagingContext): TestPost1
}

object TestPost1 extends RequestMetaCompanion[TestPost1] with TestPost1ObjectApi {
  def apply(id: String, x: String, headers: Headers)
           (implicit mcx: MessagingContext): TestPost1 = TestPost1(id, TestBody1(x), headers, Null)(mcx)

  type ResponseType = ResponseBase
  implicit val meta = this
}

@request(Method.GET, "hb://test")
case class TestGet1(id: String, body: EmptyBody = EmptyBody) extends Request[EmptyBody]

@request(Method.POST, "hb://test")
case class TestPost1DefinedResponse(id: String, body: TestBody1)
  extends Request[TestBody1]
    with DefinedResponse[Ok[TestBody2]]

@request(Method.POST, "hb://test")
case class TestPost1MultipleDefinedResponse(id: String, body: TestBody1)
  extends Request[TestBody1]
    with DefinedResponse[(Ok[TestBody2], Ok[TestBody3])]

case class TestItem(x: String, y: Int)

@body("test-collection")
case class TestCollectionBody(items: Seq[TestItem]) extends CollectionBody[TestItem]

@request(Method.POST, "hb://test")
case class TestCollectionPost(body: TestCollectionBody) extends Request[TestCollectionBody]

@body("test-case")
case class TestCaseBody(intField: Int, stringField: String) extends Body

@request(Method.POST, "hb://test/{user_id}")
case class TestCasePost(userId: String, body: TestCaseBody) extends Request[TestCaseBody]

// don't remove, this should just compile
@request(Method.POST, "hb://test")
case class TestSeqStringQueryPost(fields: Option[Seq[String]], body: TestCaseBody) extends Request[TestCaseBody]

@request(Method.POST, "hb://test")
case class TestMultipleDefinedResponseWithSameBody(id: String, body: TestBody1)
  extends Request[TestBody1]
    with DefinedResponse[(Ok[TestBody2], Created[TestBody2])]

class TestRequestAnnotation extends FlatSpec with Matchers {
  implicit val mcx = new MessagingContext {
    override def createMessageId() = "123"
    override def correlationId = "123"
    override def parentId: Option[String] = Some("123")
  }
  val rn = "\r\n"

  "TestPost1" should "serialize" in {
    val post1 = TestPost1("155", TestBody1("abcde"))
    post1.serializeToString should equal(
      s"""{"r":{"q":{"id":"155"},"l":"hb://test"},"m":"post","t":"application/vnd.test-body-1+json","i":"123"}""" + rn +
        """{"data":"abcde"}""")
  }

  "TestCollectionPost" should "serialize" in {
    val post1 = TestCollectionPost(TestCollectionBody(Seq(TestItem("abcde", 123), TestItem("eklmn", 456))))
    post1.serializeToString should equal(
      s"""{"r":{"l":"hb://test"},"m":"post","t":"application/vnd.test-collection+json","i":"123"}""" + rn +
        """[{"x":"abcde","y":123},{"x":"eklmn","y":456}]""")
  }

  "TestCollectionPost" should "deserialize" in {
    val str = s"""{"r":{"l":"hb://test"},"m":"post","t":"application/vnd.test-collection+json","i":"123"}""" + rn +
      """[{"x":"abcde","y":123},{"x":"eklmn","y":456}]"""
    TestCollectionPost.from(str) should equal (
      TestCollectionPost(TestCollectionBody(Seq(TestItem("abcde", 123), TestItem("eklmn", 456))))
    )
  }

  "Dynamic Collection" should "serialize" in {
    val post1 = DynamicRequest(HRL("hb://test"), Method.POST,
      DynamicBody(
        Lst.from(Obj.from("x" → "abcde", "y" → 123), Obj.from( "x" → "eklmn", "y" → 456))
      )
    )
    post1.serializeToString should equal(
      s"""{"r":{"l":"hb://test"},"m":"post","i":"123"}""" + rn +
        """[{"x":"abcde","y":123},{"x":"eklmn","y":456}]""")
  }

  "Dynamic Collection" should "deserialize" in {
    val str = s"""{"r":{"l":"hb://test"},"m":"post","i":"123"}""" + rn +
      """[{"x":"abcde","y":123},{"x":"eklmn","y":456}]"""
    DynamicRequest.from(str) should equal (
      DynamicRequest(HRL("hb://test"), Method.POST,
        DynamicBody(
          Lst.from(Obj.from("x" → "abcde", "y" → 123), Obj.from( "x" → "eklmn", "y" → 456))
        )
      )
    )
  }

  "TestCasePost" should "serialize" in {
    val post1 = TestCasePost("maqdev", TestCaseBody(intField=100500, stringField="Yey"))
    post1.serializeToString should equal(
      s"""{"r":{"q":{"user_id":"maqdev"},"l":"hb://test/{user_id}"},"m":"post","t":"application/vnd.test-case+json","i":"123"}""" + rn +
        """{"int_field":100500,"string_field":"Yey"}""")
  }

  "TestCasePost" should "deserialize" in {
    val str = s"""{"r":{"q":{"user_id":"maqdev"},"l":"hb://test/{user_id}"},"m":"post","t":"application/vnd.test-case+json","i":"123"}""" + rn +
      """{"int_field":100500,"string_field":"Yey"}"""
    TestCasePost.from(str) should equal (
      TestCasePost("maqdev", TestCaseBody(intField=100500, stringField="Yey"))
    )
  }


  "TestPost1DefinedResponse" should "serialize" in {
    val post1 = TestPost1DefinedResponse("155", TestBody1("abcde"))
    post1.serializeToString should equal(
      s"""{"r":{"q":{"id":"155"},"l":"hb://test"},"m":"post","t":"application/vnd.test-body-1+json","i":"123"}""" + rn +
        """{"data":"abcde"}""")
  }

  "TestPost1DefinedResponse" should " have meta" in {
    val requestMeta  = implicitly[RequestMeta[TestPost1DefinedResponse]]
    val observableMeta = implicitly[RequestObservableMeta[TestPost1DefinedResponse]]

    observableMeta.requestMatcher should equal(RequestMatcher("hb://test", "post", Some("test-body-1")))

    val s = """{"s":200,"t":"test-body-2","i":"123","r":{"l":"hb://test"}}""" + rn +
      """{"x":"100500","y":555}"""

    val response: requestMeta.ResponseType = MessageReader.fromString(s, requestMeta.responseDeserializer)
    response.body should equal (TestBody2("100500",555))
    // response.body.x should equal("100500") // todo: make something to work this
    // response.body.y should equal(555)
    response shouldBe a[Ok[_]]
  }

  "TestPost1MultipleDefinedResponse" should " have meta" in {
    val requestMeta = implicitly[RequestMeta[TestPost1MultipleDefinedResponse]]
    val observableMeta = implicitly[RequestObservableMeta[TestPost1MultipleDefinedResponse]]

    observableMeta.requestMatcher should equal(RequestMatcher("hb://test", "post", Some("test-body-1")))

    val s1 = """{"s":200,"t":"test-body-2","i":"123","r":{"l":"hb://test"}}""" + rn +
      """{"x":"100500","y":555}"""

    val response1: requestMeta.ResponseType = MessageReader.fromString(s1, requestMeta.responseDeserializer)
    response1.body should equal (TestBody2("100500",555))
    response1 shouldBe a[Ok[_]]

    val s2 = """{"s":200,"t":"test-body-3","i":"123","r":{"l":"hb://test"}}""" + rn +
      """{"x":"100500","y":555, "z": 888}"""

    val response2: requestMeta.ResponseType = MessageReader.fromString(s2, requestMeta.responseDeserializer)
    response2.body should equal (TestBody3("100500",555, 888l))
    response2 shouldBe a[Ok[_]]
  }

  "TestMultipleDefinedResponseWithSameBody" should " have meta" in {
    val requestMeta = implicitly[RequestMeta[TestMultipleDefinedResponseWithSameBody]]
    val observableMeta = implicitly[RequestObservableMeta[TestMultipleDefinedResponseWithSameBody]]

    val s1 = """{"s":200,"t":"test-body-2","i":"123","r":{"l":"hb://test"}}""" + rn +
      """{"x":"100500","y":555}"""

    val response1: requestMeta.ResponseType = MessageReader.fromString(s1, requestMeta.responseDeserializer)
    response1.body should equal (TestBody2("100500",555))
    response1 shouldBe a[Ok[_]]
    // this doesn't work :-( response1.body.x shouldBe "100500" // don't remove, this should compile
    // response1.body.y shouldBe 555

    val s2 = """{"s":201,"t":"test-body-2","i":"123","r":{"l":"hb://test"}}""" + rn +
      """{"x":"100500","y":555}"""

    val response2: requestMeta.ResponseType = MessageReader.fromString(s2, requestMeta.responseDeserializer)
    response2.body should equal (TestBody2("100500",555))
    response2 shouldBe a[Created[_]]
  }

  "TestGet1 (with EmptyBody)" should "serialize" in {
    val r = TestGet1("155")
    r.serializeToString should equal(
      s"""{"r":{"q":{"id":"155"},"l":"hb://test"},"m":"get","i":"123"}""" + rn + "{}")
  }

  "TestGet1 (with EmptyBody)" should "deserialize" in {
    val str = s"""{"r":{"q":{"id":"155"},"l":"hb://test"},"m":"get","i":"123"}""" + rn + "{}"
    TestGet1.from(str) should equal (TestGet1("155"))
  }

  "TestPost1" should "serialize with headers" in {
    val post1 = TestPost1("155", TestBody1("abcde"), Headers("test" → Lst.from("a")))
    post1.serializeToString should equal(
      s"""{"test":["a"],"r":{"q":{"id":"155"},"l":"hb://test"},"m":"post","t":"application/vnd.test-body-1+json","i":"123"}""" + rn +
         """{"data":"abcde"}""")
  }

  "TestPost1" should "serialize and headers don't override class arguments" in {
    val post1 = TestPost1("155", TestBody1("abcde"), Headers("test" → Lst.from("a"), Header.HRL → HRL("hb://abc").toValue))
    post1.serializeToString should equal(
      s"""{"test":["a"],"r":{"q":{"id":"155"},"l":"hb://test"},"m":"post","t":"application/vnd.test-body-1+json","i":"123"}""" + rn +
        """{"data":"abcde"}""")
  }

  "TestPost1" should "serialize with extra query" in {
    val post1 = TestPost1("155", TestBody1("abcde"), Headers.empty, Obj.from("a" → "100500"))
    post1.serializeToString should equal(
      s"""{"r":{"q":{"id":"155","a":"100500"},"l":"hb://test"},"m":"post","t":"application/vnd.test-body-1+json","i":"123"}""" + rn +
        """{"data":"abcde"}""")
  }

  "TestPost1" should "deserialize" in {
    val str = """{"r":{"q":{"id":"155"},"l":"hb://test-post-1"},"m":"post","t":"application/vnd.test-body-1+json","i":"123"}""" + rn +
      """{"data":"abcde"}"""
    val post = TestPost1.from(str)
    post.headers.hrl should equal(HRL("hb://test-post-1", Obj.from("id" -> "155")))
    post.headers.contentType should equal(Some("test-body-1"))
    post.headers.method should equal("post")
    post.headers.messageId should equal("123")
    post.headers.correlationId should equal("123")

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
    str should equal("""{"r":{"l":"hb://test-outer-resource"},"m":"get","t":"test-outer-body","i":"123"}""" + rn +
      """{"outerData":"abcde","_embedded":{"simple":{"innerData":"eklmn","_links":{"self":{"r":{"l":"hb://test-inner-resource"}}}},"collection":[{"innerData":"xyz","_links":{"self":{"r":{"l":"hb://test-inner-resource"}}}},{"innerData":"yey","_links":{"self":{"r":{"l":"hb://test-inner-resource"}}}}]}}""")
  }

  "TestOuterPost" should "deserialize" in {
    val str = """{"r":{"l":"hb://test-outer-resource"},"m":"get","t":"test-outer-body","i":"123"}""" + rn +
      """{"outerData":"abcde","_embedded":{"simple":{"innerData":"eklmn","_links":{"self":{"r":{"l":"hb://test-inner-resource"}}}},"collection":[{"innerData":"xyz","_links":{"self":{"r":{"l":"hb://test-inner-resource"}}}},{"innerData":"yey","_links":{"self":{"r":{"l":"hb://test-inner-resource"}}}}]}}"""

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
    val str = """{"r":{"l":"hb://test-outer-resource"},"m":"custom-method","t":"test-body-1","i":"123"}""" + rn +
      """{"resource_id":"100500"}"""
    val request = DynamicRequest.from(str)
    request shouldBe a[Request[_]]
    request.headers.method should equal("custom-method")
    request.headers.hrl should equal(HRL("hb://test-outer-resource"))
    request.headers.messageId should equal("123")
    request.correlationId should equal("123")
    request.body should equal(DynamicBody(Obj.from("resource_id" -> "100500"), Some("test-body-1")))
  }

  it should "lower-case headers" in {
    val str = """{"r":{"l":"hb://test-outer-resource"},"m":"custom-method","t":"test-body-1","i":"123","Some-Header":"abc"}""" + rn +
      """{"resource_id":"100500"}"""
    val request = DynamicRequest.from(str)
    request shouldBe a[Request[_]]
    request.headers("some-header") shouldBe Text("abc")
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

