package com.hypertino.hyperbus.model.headers

import com.hypertino.binders.value.{Lst, Obj, Text}

object PlainHeadersConverter extends HeadersConverter {
  override def fromHttp(headers: Seq[(String, String)]): Obj = {
    Obj(headers.groupBy(_._1).map { case (k, v) ⇒
      if (v.tail.isEmpty) { // just one element
        k → Text(v.head._2)
      } else {
        k → Lst(v.map(i ⇒ Text(i._2)))
      }
    })
  }

  override def toHttp(headers: Obj): Seq[(String, String)] = {
    headers.v.toSeq.flatMap {
      case (k,Lst(elements)) ⇒ elements.map(e ⇒ (k, e.toString))
      case (k, other) ⇒ Seq((k, other.toString))
    }
  }
}
