package testclasses

import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.model.annotations.{body, request}

@body("test-1")
case class TestBody1(resourceData: String) extends Body

@body("test-2")
case class TestBody2(resourceData: Long) extends Body

@body("created-body")
case class TestCreatedBody(resourceId: String) extends Body

@body("some-another-body")
case class TestAnotherBody(resourceId: String) extends Body

@body
case class TestBodyNoContentType(resourceData: String) extends Body

@request(Method.POST, "hb://resources")
case class TestPost1(body: TestBody1) extends Request[TestBody1]
  with DefinedResponse[Created[TestCreatedBody]]

@request(Method.POST, "hb://resources")
case class TestPost2(body: TestBody2) extends Request[TestBody2]
  with DefinedResponse[Created[TestCreatedBody]]

@request(Method.POST, "hb://resources")
case class TestPost3(body: TestBody2) extends Request[TestBody2]
  with DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])]

@request(Method.POST, "hb://empty")
case class TestPostWithNoContent(body: TestBody1) extends Request[TestBody1]
  with DefinedResponse[NoContent[EmptyBody]]

@request(Method.POST, "hb://empty")
case class StaticPostWithDynamicBody(body: DynamicBody) extends Request[DynamicBody]
  with DefinedResponse[NoContent[EmptyBody]]

@request(Method.POST, "hb://empty")
case class StaticPostWithEmptyBody(body: EmptyBody) extends Request[EmptyBody]
  with DefinedResponse[NoContent[EmptyBody]]

@request(Method.GET, "hb://empty")
case class StaticGetWithQuery(body: EmptyBody) extends Request[EmptyBody]
  with DefinedResponse[Ok[DynamicBody]]

@request(Method.POST, "hb://content-body-not-specified")
case class StaticPostBodyWithoutContentType(body: TestBodyNoContentType) extends Request[TestBodyNoContentType]
  with DefinedResponse[NoContent[EmptyBody]]

@request(Method.PUT, "hb://2resp")
case class TestPostWith2Responses(body: TestBody1) extends Request[TestBody1]
  with DefinedResponse[(Created[TestCreatedBody], Ok[TestAnotherBody])]


@body("some-transaction")
case class SomeTransaction(transactionId: String) extends Body

@body("some-transaction-created")
case class SomeTransactionCreated(
                                      transactionId: String,
                                      path: String
                                    ) extends Body

@request(Method.PUT, "/some/{path:*}")
case class SomeContentPut(
                              path: String,
                              body: DynamicBody
                            ) extends Request[DynamicBody]
  with DefinedResponse[(
    Ok[SomeTransaction],
      Created[SomeTransactionCreated]
    )]


@request(Method.GET, "hb://test-optional-query-params")
case class TestGetWithQuery(a: Option[String], body: EmptyBody) extends Request[EmptyBody]
  with DefinedResponse[Ok[TestAnotherBody]]


