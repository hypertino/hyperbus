package eu.inn.hyperbus.rest

import java.io.ByteArrayOutputStream

import eu.inn.binders.annotations.fieldName
import eu.inn.hyperbus.rest.annotations.body
import eu.inn.hyperbus.rest.standard._
import org.scalatest.{FreeSpec, Matchers}

@body("test-created-body")
case class TestCreatedBody(resourceId: String,
                           @fieldName("_links") links: Body.LinksMap = Map(
                             DefLink.LOCATION -> Left(Link("/resources/{resourceId}", templated = Some(true)))))
  extends CreatedBody// with NoContentType


class TestResponseAnnotation extends FreeSpec with Matchers {
  "Response Annotation " - {
    "Encode Response" in {
      val msg = new Created(TestCreatedBody("100500"), messageId = "123", correlationId = "abc")
      val ba = new ByteArrayOutputStream()
      msg.encode(ba)
      val s = ba.toString("UTF8")
      //println(s)
      s should equal("""{"response":{"status":201,"contentType":"test-created-body","messageId":"123","correlationId":"abc"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}""")
    }
  }
}
