package eu.inn.hyperbus.model

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.hyperbus.model.annotations.request
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api.{Filters, SpecificValue, Topic}
import org.scalatest.{FreeSpec, Matchers}

@request("/test-post-1/{id}")
case class TestPost1(body: TestBody1) extends Request[TestBody1] {
  override def method: String = "test-method"
}

object TestPost1 {
  def apply(x: String): TestPost1 = TestPost1(TestBody1(x))
}

class TestRequestAnnotation extends FreeSpec with Matchers {
  "Request Annotation " - {

    "TestPost1 should serialize" in {
      val ba = new ByteArrayOutputStream()
      val post1 = TestPost1(TestBody1("155", "abcde"), messageId = "123", correlationId = "123")
      post1.serialize(ba)
      val str = ba.toString("UTF-8")
      str should equal("""{"request":{"url":"/test-post-1/{id}","method":"test-method","contentType":"test-body-1","messageId":"123"},"body":{"id":"155","data":"abcde"}}""")
    }

    "TestPost1 should deserialize" in {
      val str = """{"request":{"url":"/test-post-1/{id}","method":"test-method","contentType":"test-body-1","messageId":"123"},"body":{"id":"155","data":"abcde"}}"""
      val bi = new ByteArrayInputStream(str.getBytes("UTF-8"))
      val post1 = MessageDeserializer.deserializeRequestWith(bi) { (requestHeader, jsonParser) ⇒
        requestHeader.url should equal("/test-post-1/{id}")
        requestHeader.contentType should equal(Some("test-body-1"))
        requestHeader.method should equal("test-method")
        requestHeader.messageId should equal("123")
        requestHeader.correlationId should equal(None)
        TestPost1(TestBody1(requestHeader.contentType, jsonParser))
      }

      post1.body should equal(TestBody1("155", "abcde"))
      post1.topic should equal(Topic("/test-post-1/{id}", Filters(Map(
        "id" → SpecificValue("155")
      ))))
    }

    /*
todo: fix test
    "Decode DynamicRequest" in {
      val s = """{"request":{"method":"get","url":"/test"},"body":{"resourceId":"100500"}}"""
      val is = new ByteArrayInputStream(s.getBytes("UTF8"))
      val d = Helpers.decodeRequestWith(is) { (rh, is2) =>
        Helpers.decodeDynamicRequest(rh, is2)
      }

      d should equal(new DynamicGet("/test",DynamicBody(Obj(Map("resourceId" -> Text("100500"))))))
    }*/

  }
}