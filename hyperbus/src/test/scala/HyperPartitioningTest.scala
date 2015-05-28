import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.impl.Helpers
import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.protocol.annotations.{url, contentType}
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.transport.{ExactValue, PartitionArgs, Topic, InprocTransport}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Future

@contentType("application/vnd+parition.json")
case class TestPartition(partitionId: String, data: String) extends Body

@url("/resources/{partitionId}")
case class TestPostPartition1(body: TestPartition) extends StaticPost(body)
with DefinedResponse[Ok[DynamicBody]]


class HyperPartitioningTest extends FreeSpec with Matchers with ScalaFutures {
  import scala.concurrent.ExecutionContext.Implicits.global

  "HyperPartitioning " - {
    "Partitioning when asking" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":200},"body":{}}"""
      )

      val hyperBus = new HyperBus(new ServiceBus(ct,null))
      val f = hyperBus ? TestPostPartition1(TestPartition("1", "ha"))

      ct.inputTopic should equal (
        Topic("/resources/{partitionId}", PartitionArgs(Map("partitionId" → ExactValue("1"))))
      )

      whenReady(f) { r =>
        r.body should equal(DefaultDynamicBody(Obj()))
      }
    }

    "Partitioning when serving" in {
      val st = new ServerTransportTest()
      val hyperBus = new HyperBus(new ServiceBus(null,st))
      hyperBus.on[TestPostPartition1] { post =>
        Future {
          Ok(DefaultDynamicBody(Obj()))
        }
      }

      val req = """{"request":{"url":"/resources/{partitionId}","method":"post","contentType":"application/vnd+parition.json"},"body":{"partitionId":"123","data":"abc"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDecoder(ba)
      msg should equal(TestPostPartition1(TestPartition("123", "abc")))

      val partitionArgs = st.sExtractor(msg)
      partitionArgs should equal(
        PartitionArgs(Map("partitionId" → ExactValue("123")))
      )
    }

    "Parse Url" in {
      val p: String ⇒ Seq[String] = Helpers.extractParametersFromUrl
      p("{abc}") should equal(Seq("abc"))
      p("/{abc}/") should equal(Seq("abc"))
      p("x/{abc}/y") should equal(Seq("abc"))
      p("x/{abc}/y/{def}") should equal(Seq("abc", "def"))
      p("{abc}{def}") should equal(Seq("abc", "def"))
    }
  }
}
