/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */


/*

@body("application/vnd+parition.json")
case class TestPartition(data: String) extends Body

@request(Method.POST, "/resources/{partitionId}")
case class TestPostPartition1(partitionId: String, body: TestPartition) extends Request[TestPartition]
  with DefinedResponse[Ok[DynamicBody]]


class HyperPartitioningTest extends FreeSpec with Matchers with ScalaFutures {

  //todo: real partition test (with different suscribers)
  "HyperPartitioning " - {
    "Partitioning when asking" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":200,"message_id":"123"},"body":{}}"""
      )

      val hyperbus = newHyperbus(ct, null)
      val f = hyperbus ask TestPostPartition1(TestPartition("1", "ha"), messageId = "123", correlationId = "123")

      ct.inputTopic should equal(
        Topic("/resources/{partitionId}", Filters(Map("partitionId" → SpecificValue("1"))))
      )

      whenReady(f) { r =>
        r.body should equal(DynamicBody(Obj()))
      }
    }

    "Partitioning when serving" in {
      val st = new ServerTransportTest()
      val hyperbus = newHyperbus(null, st)
      hyperbus commands { post: TestPostPartition1 =>
        Future {
          Ok(DynamicBody(Obj()))
        }
      }

      val req = """{"request":{"url":"/resources/{partitionId}","method":"post","contentType":"application/vnd+parition.json","message_id":"123"},"body":{"partitionId":"123","data":"abc"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDecoder(ba)
      msg should equal(TestPostPartition1(TestPartition("123", "abc"), messageId = "123", correlationId = "123"))

      msg.topic.valueFilters should equal(
        Filters(Map("partitionId" → SpecificValue("123")))
      )
    }
  }

  // todo: add partition tests for Dynamic

  def newHyperbus(ct: ClientTransport, st: ServerTransport) = {
    val cr = List(TransportRoute(ct, RequestBaseMatcher(Some(Uri(AnyValue)))))
    val sr = List(TransportRoute(st, RequestBaseMatcher(Some(Uri(AnyValue)))))
    val transportManager = new TransportManager(cr, sr, ExecutionContext.global)
    new Hyperbus(transportManager, logMessages = true)
  }
}

*/