package com.hypertino.hyperbus.model

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.annotations.body
import org.scalatest.{FlatSpec, Matchers}

@body("test-created-body")
case class TestCreatedBody(resourceId: String) extends Body


class TestResponseAnnotation extends FlatSpec with Matchers {
  val rn = "\r\n"
  implicit val mcx = new MessagingContext {
    override def createMessageId() = "123"
    override def correlationId = Some("abc")
  }

  "Response" should "serialize" in {
    val msg = Created(TestCreatedBody("100500"), HRI("hb://test"))
    msg.serializeToString should equal("""{"t":"test-created-body","i":"123","c":"abc","l":{"a":"hb://test"},"s":201}""" + rn +
      """{"resourceId":"100500"}""")
  }

  "Response with headers" should "serialize" in {
    val msg = Created(TestCreatedBody("100500"), HRI("hb://test"), Obj.from("test" → "a"))
    msg.serializeToString should equal("""{"status":201,"headers":{"test":["a"],"messageId":"123","correlationId":"abc","contentType":"test-created-body"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}""")
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
    r1.productElement(1) shouldBe a[Map[_, _]]

    val o: Any = r1
    o match {
      case Created(body, headers) ⇒
        body shouldBe a[TestCreatedBody]
        headers shouldBe a[Map[_, _]]
      case _ ⇒ fail("unapply didn't matched for a response")
    }
  }
}

