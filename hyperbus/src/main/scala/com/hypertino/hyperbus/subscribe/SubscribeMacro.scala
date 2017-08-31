package com.hypertino.hyperbus.subscribe

import com.hypertino.binders.util.MacroAdapter
import com.hypertino.hyperbus.model.{Request, RequestBase}
import com.hypertino.hyperbus.model.annotations.RequestAnnotationMacroImpl
import monix.eval.Task
import monix.execution.{Ack, Cancelable}
import org.slf4j.Logger

import scala.reflect.macros.blackbox.Context

private[hyperbus] object SubscribeMacro {
  def subscribeWithLog[A: c.WeakTypeTag](c: Context)(serviceClass: c.Expr[A], log: c.Expr[Logger]): c.Expr[Seq[Cancelable]] = {
    val c0: c.type = c
    val bundle = new {
      val ctx: c0.type = c0
    } with SubscribeMacroImpl[Context]
    bundle.subscribe(serviceClass, Some(log))
  }

  def subscribe[A: c.WeakTypeTag](c: Context)(serviceClass: c.Expr[A]): c.Expr[Seq[Cancelable]] = {
    val c0: c.type = c
    val bundle = new {
      val ctx: c0.type = c0
    } with SubscribeMacroImpl[Context]
    bundle.subscribe(serviceClass, None)
  }
}

trait SubscribeMacroImpl[C <: Context] extends MacroAdapter[C] {
  val ctx: C

  import ctx.universe._

  def subscribe[A: ctx.WeakTypeTag](serviceClass: ctx.Expr[A], log: Option[ctx.Expr[Logger]]): ctx.Expr[Seq[Cancelable]] = {
    val commandMethods = extractCommandMethods[A]
    val eventMethods = extractEventMethods[A]
    if (commandMethods.isEmpty && eventMethods.isEmpty) {
      ctx.abort(ctx.enclosingPosition, s"No suitable method is defined in ${weakTypeOf[A]}")
    }

    val tVar = fresh("t")
    val taskVar = fresh("task")

    val commandSubscriptions = commandMethods.map { case (m, t) ⇒
      val typeSymbol = t.typeSignature
      q"""
        $tVar.commands[$typeSymbol].subscribe{ implicit c ⇒
          val $taskVar: monix.eval.Task[com.hypertino.hyperbus.model.ResponseBase] = monix.eval.Task.fromTry{
            scala.util.Try {
              $m(c.request)
            }
          }.flatten

          $taskVar
            .onErrorRecover(com.hypertino.hyperbus.subscribe.SubscribeMacroUtil.convertUnhandledException($log))
            .runOnComplete(c.reply)

          monix.execution.Ack.Continue
        }
      """
    }

    val eventSubscriptions = eventMethods.map { case (m, t) ⇒
      val groupName = methodGroupName(m)
      val typeSymbol = t.typeSignature
      q"""
        $tVar.events[$typeSymbol]($groupName).subscribe{ implicit e ⇒
          $m(e)
        }
      """
    }

    val block = ctx.Expr[Seq[Cancelable]](q"""{
      val $tVar = ${ctx.prefix.tree}
      Seq[monix.execution.Cancelable](
        ..$commandSubscriptions,
        ..$eventSubscriptions
      )
     }""")

    //println(block)
    block
  }

  protected def extractOnMethods[A: ctx.WeakTypeTag]: Seq[(MethodSymbol, Symbol)] = {
    val rts = weakTypeOf[RequestBase]//.typeSymbol//.typeSignature

    weakTypeOf[A].members.flatMap { member ⇒
      if (member.isMethod && member.isPublic && member.name.decodedName.toString.startsWith("on")) {
        val m = member.asInstanceOf[MethodSymbol]
        if ((m.paramLists.size == 1 && m.paramLists.head.size == 1) ||
          (m.paramLists.size == 2 && m.paramLists.head.size == 1 && allImplicits(m.paramLists.tail))) {

          val paramSymbol = m.paramLists.head.head
          val paramType = paramSymbol.typeSignature
          // println(m, paramType, paramSymbol, rts)
          if (paramType <:< rts) {
            Some((m, paramSymbol))
          }
          else {
            None
          }
        }
        else {
          None
        }
      }
      else {
        None
      }
    }.toSeq
  }

  protected def extractCommandMethods[A: ctx.WeakTypeTag]: Seq[(MethodSymbol, Symbol)] = {
    val tts = weakTypeOf[Task[_]]
    val all = extractOnMethods[A]
    // println(all)
    all.filter(_._1.returnType <:< tts)
  }

  protected def extractEventMethods[A: ctx.WeakTypeTag]: Seq[(MethodSymbol, Symbol)] = {
    val tts = weakTypeOf[Ack]//.typeSymbol.typeSignature
    val all = extractOnMethods[A]
    //println(all)
    all.filter(_._1.returnType <:< tts)
  }

  private def allImplicits(symbols: List[List[Symbol]]): Boolean = !symbols.flatten.exists(!_.isImplicit)

  protected def fresh(prefix: String): TermName = TermName(ctx.freshName(prefix))

  protected def methodGroupName(symbol: ctx.Symbol): Option[String] = {
    val annotation = symbol.annotations.find(a => a.treeTpe == typeOf[com.hypertino.hyperbus.subscribe.annotations.groupName])
    annotation.map { a =>
      a.arguments.head match {
        case Literal(Constant(s:String)) => Some(s)
        case _ => None
      }
    } getOrElse {
      None
    }
  }
}