package com.hypertino.hyperbus.model

import com.hypertino.hyperbus.model.annotations.body
import org.scalatest.{FlatSpec, Matchers}

@body("test-body-1")
case class TestBody1(data: String) extends Body

@body("test-body-2")
case class TestBody2(x: String, y: Int) extends Body

@body("test-body-3")
case class TestBody3(x: String, y: Int, z: Long) extends Body

class TestBodyAnnotation extends FlatSpec with Matchers {
  "Body" should "serialize" in {
    import com.hypertino.binders.json.JsonBinders._
    val body = TestBody1("abcde")
    val s = body.toJson
    s should equal("""{"data":"abcde"}""")
  }

  "Body" should "deserialize" in {
    import com.hypertino.binders.json.JsonBinders._
    val s = """{"data":"abcde"}"""
    val body = s.parseJson[TestBody1]
    body should equal(TestBody1("abcde"))
  }
}
