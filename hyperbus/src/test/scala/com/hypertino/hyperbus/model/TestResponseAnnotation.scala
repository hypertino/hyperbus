package com.hypertino.hyperbus.model

import java.io.Reader

import com.hypertino.binders.core.BindOptions
import com.hypertino.hyperbus.model.annotations.body
import com.hypertino.hyperbus.serialization.{MessageReader, SerializationOptions}
import org.scalatest.{FlatSpec, Matchers}
import testclasses.StaticGetWithQuery

import scala.util.Try

@body("test-created-body")
case class TestCreatedBody(resourceId: String, nullable: Option[String]=None) extends Body


class TestResponseAnnotation extends FlatSpec with Matchers {
  val rn = "\r\n"
  implicit val mcx = new MessagingContext {
    override def createMessageId() = "1"
    override def correlationId = "2"
    override def parentId: Option[String] = Some("3")
  }

  "Response" should "serialize" in {
    val msg = Created(TestCreatedBody("100500"), HRL("hb://test"))
    msg.serializeToString should equal("""{"s":201,"t":"application/vnd.test-created-body+json","i":"1","c":"2","p":"3","l":{"l":"hb://test"}}""" + rn +
      """{"resource_id":"100500"}""")
  }

  "Response with forced null's" should "serialize" in {
    implicit val so = SerializationOptions.forceOptionalFields
    val msg = Created(TestCreatedBody("100500"), HRL("hb://test"))
    msg.serializeToString should equal("""{"s":201,"t":"application/vnd.test-created-body+json","i":"1","c":"2","p":"3","l":{"q":null,"l":"hb://test"}}""" + rn +
      """{"resource_id":"100500","nullable":null}""")
  }

  "Response" should "deserialize" in {
    val s = """{"s":201,"t":"application/vnd.test-created-body+json","i":"1","c":"2","p":"3","l":{"l":"hb://test"}}""" + rn +
      """{"resource_id":"100500"}"""

    val deserializer = StandardResponse.apply(_: Reader, _: Headers, {
      case h: ResponseHeaders if h.contentType.contains("test-created-body") ⇒ TestCreatedBody.apply
    },
      false
    )

    val o = Created(TestCreatedBody("100500"), HRL("hb://test"))
    val response = MessageReader.fromString(s, deserializer)
    response.body should equal (o.body)
    response.headers.toSet should equal(o.headers.toSet)
  }

  "Response with DynamicBody" should "deserialize ErrorBody" in {
    val s = """{"s":409,"i":"abcde12345"}""" + "\r\n" + """{"code":"failed","error_id":"abcde12345"}"""
    val deserializer = StaticGetWithQuery.responseDeserializer
    val t = Try(MessageReader.fromString(s, deserializer))
    t.isFailure shouldBe true
    val response = t.failed.get.asInstanceOf[HyperbusError[ErrorBody]]
    response.headers.statusCode shouldBe 409
    response.body shouldBe ErrorBody("failed", errorId="abcde12345")
  }

  "Response with headers" should "serialize" in {
    val msg = Created(TestCreatedBody("100500"), HRL("hb://test"), Headers("test" → "a"))
    msg.serializeToString should equal("""{"s":201,"t":"application/vnd.test-created-body+json","i":"1","c":"2","p":"3","l":{"l":"hb://test"},"test":"a"}""" + rn +
      """{"resource_id":"100500"}""")
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

  "Ok[TestCollectionBody]" should "serialize" in {
    val ok = Ok(TestCollectionBody(Seq(TestItem("abcde", 123), TestItem("eklmn", 456))))
    ok.serializeToString should equal(
      s"""{"s":200,"t":"application/vnd.test-collection+json","i":"1","c":"2","p":"3"}""" + rn +
        """[{"x":"abcde","y":123},{"x":"eklmn","y":456}]""")
  }

  it should "deserialize" in {
    val s =  s"""{"s":200,"t":"application/vnd.test-collection+json","i":"1","c":"2","p":"3"}""" + rn +
      """[{"x":"abcde","y":123},{"x":"eklmn","y":456}]"""

    val deserializer = StandardResponse.apply(_: Reader, _: Headers, {
      case h: ResponseHeaders if h.contentType.contains("test-collection") ⇒ TestCollectionBody.apply
    },
      false
    )

    val o = Ok(TestCollectionBody(Seq(TestItem("abcde", 123), TestItem("eklmn", 456))))
    val response = MessageReader.fromString(s, deserializer)
    response.body should equal (o.body)
    response.headers.toSet should equal(o.headers.toSet)
  }
}

