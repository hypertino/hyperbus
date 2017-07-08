package com.hypertino.hyperbus.model

import java.io.Reader

import com.hypertino.binders.core.BindOptions
import com.hypertino.hyperbus.model.annotations.body
import com.hypertino.hyperbus.serialization.MessageReader
import org.scalatest.{FlatSpec, Matchers}

@body("test-created-body")
case class TestCreatedBody(resourceId: String, nullable: Option[String]=None) extends Body


class TestResponseAnnotation extends FlatSpec with Matchers {
  val rn = "\r\n"
  implicit val mcx = new MessagingContext {
    override def createMessageId() = "123"
    override def correlationId = "abc"
  }

  "Response" should "serialize" in {
    val msg = Created(TestCreatedBody("100500"), HRL("hb://test"))
    msg.serializeToString should equal("""{"s":201,"t":"application/vnd.test-created-body+json","i":"123","c":"abc","l":{"l":"hb://test"}}""" + rn +
      """{"resourceId":"100500"}""")
  }

  "Response with forced null's" should "serialize" in {
    implicit val bindOptions = BindOptions(skipOptionalFields = false)
    val msg = Created(TestCreatedBody("100500"), HRL("hb://test"))
    msg.serializeToString should equal("""{"s":201,"t":"application/vnd.test-created-body+json","i":"123","c":"abc","l":{"q":null,"l":"hb://test"}}""" + rn +
      """{"resourceId":"100500","nullable":null}""")
  }

  "Response" should "deserialize" in {
    val s = """{"s":201,"t":"application/vnd.test-created-body+json","i":"123","c":"abc","l":{"l":"hb://test"}}""" + rn +
      """{"resourceId":"100500"}"""

    val deserializer = StandardResponse.apply(_: Reader, _: HeadersMap, {
      case h: ResponseHeaders if h.contentType.contains("test-created-body") ⇒ TestCreatedBody.apply
    })

    val o = Created(TestCreatedBody("100500"), HRL("hb://test"))
    val response = MessageReader.fromString(s, deserializer)
    response.body should equal (o.body)
    response.headers.toSet should equal(o.headers.toSet)
  }

  "Response with headers" should "serialize" in {
    val msg = Created(TestCreatedBody("100500"), HRL("hb://test"), HeadersMap("test" → "a"))
    msg.serializeToString should equal("""{"s":201,"t":"application/vnd.test-created-body+json","i":"123","c":"abc","l":{"l":"hb://test"},"test":"a"}""" + rn +
      """{"resourceId":"100500"}""")
  }

  "hashCode, equals, product" should "work" in {
    val r1 = Created(TestCreatedBody("100500"))
    val r2 = Created(TestCreatedBody("100500"))
    val r3 = Created(TestCreatedBody("1005001"))
    r1 should equal(r2)
    r1.hashCode() should equal(r2.hashCode())
    r1 shouldNot equal(r3)
    r1.hashCode() shouldNot equal(r3.hashCode())
    r1.productElement(0) shouldBe a[TestCreatedBody]
    r1.productElement(1) shouldBe a[ResponseHeaders]

    val o: Any = r1
    o match {
      case Created(body, headers) ⇒
        body shouldBe a[TestCreatedBody]
        headers shouldBe a[ResponseHeaders]
      case _ ⇒ fail("unapply didn't matched for a response")
    }
  }
}

