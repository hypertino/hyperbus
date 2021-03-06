/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model.annotations

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.internal.Trees
import scala.reflect.macros.blackbox.Context

@compileTimeOnly("enable macro paradise to expand macro annotations")
class response(statusCode: Int) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro ResponseMacro.response
}

private[annotations] object ResponseMacro {
  def response(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with ResponseAnnotationMacroImpl
    bundle.run(annottees)
  }
}

private[annotations] trait ResponseAnnotationMacroImpl extends AnnotationMacroImplBase {

  import c.universe._

  def updateClass(existingClass: ClassDef, clzCompanion: Option[ModuleDef] = None): c.Expr[Any] = {
    val statusCode = c.prefix.tree match {
      case q"new response($statusCode)" => c.Expr(statusCode)
      case _ ⇒ c.abort(c.enclosingPosition, "Please provide arguments for @response annotation")
    }

    val q"case class $className[..$typeArgs](..$fields) extends ..$bases { ..$body }" = existingClass

    if (typeArgs.size != 1) {
      c.abort(c.enclosingPosition, "One type parameter is expected for a response: [T <: Body]")
    }

    val methodTypeArgs = typeArgs.map { t: TypeDef ⇒
      TypeDef(Modifiers(), t.name, t.tparams, t.rhs)
    }
    val classTypeNames = typeArgs.map { t: TypeDef ⇒
      t.name
    }
    val upperBound = typeArgs.head.asInstanceOf[TypeDef].rhs match {
      case TypeBoundsTree(_, upper) ⇒ upper
      case _ ⇒ c.abort(c.enclosingPosition, "Type bounds aren't found: [T <: Body]")
    }

    val fieldsExceptHeaders = fields.filterNot(_.name.decodedName.toString == "headers")

    val equalExpr = fieldsExceptHeaders.map(_.name).foldLeft(q"(o.headers == this.headers)") { (cap, name) ⇒
      q"(o.$name == this.$name) && $cap"
    }

    val cases = fieldsExceptHeaders.map(_.name).zipWithIndex.map { case (name, idx) ⇒
      cq"$idx => this.$name"
    } :+ cq"${fieldsExceptHeaders.size} => this.headers"

    // S -> fresh term
    val newClass =
      q"""
        @com.hypertino.hyperbus.model.annotations.statusCode($statusCode)
        class $className[..$typeArgs](..$fieldsExceptHeaders,
          val headers: com.hypertino.hyperbus.model.ResponseHeaders, plain__init: Boolean)
          extends ..$bases with scala.Product with scala.Serializable {
          def statusCode: Int = ${className.toTermName}.statusCode

          def copy[S <: $upperBound](body: S = this.body, headers: com.hypertino.hyperbus.model.ResponseHeaders = this.headers): $className[S] = {
            new $className(body, headers, plain__init = true)
          }

          override def copyWithHeaders(headers: com.hypertino.hyperbus.model.Headers) = this.copy(
              headers=com.hypertino.hyperbus.model.MessageHeaders
                .builder
                .++=(this.headers)
                .++=(headers)
                .responseHeaders()
            )

          def canEqual(other: Any): Boolean = other.isInstanceOf[$className[_ <: $upperBound]]

          override def equals(other: Any) = this.eq(other.asInstanceOf[AnyRef]) || {
            if (other.isInstanceOf[$className[_ <: $upperBound]]) {
              val o = other.asInstanceOf[$className[_ <: $upperBound]]
              $equalExpr
            } else {
              false
            }
          }

          override def hashCode: Int = scala.runtime.ScalaRunTime._hashCode(this)
          override def productArity: Int = ${fieldsExceptHeaders.size + 1}
          override def productElement(n: Int): Any = n match {
            case ..$cases
            case _ => throw new IndexOutOfBoundsException(n.toString())
          }
        }
      """

    val companionParts = clzCompanion.map { existingCompanion ⇒
      val q"object $companion extends ..$bases { ..$body }" = existingCompanion
      (companion,bases,body)
    }


    val errorApply = if (companionParts.exists(_._2.exists(_.toString.contains("ErrorResponseMeta")))) { // todo: this (check by string is a total hack, do something)
      q"""
        def apply()(implicit messagingContext: MessagingContext): $className[ErrorBody] = apply(ErrorBody(statusCodeToName(this.statusCode)))
       """
    }
    else {
      q""
    }

    val ctxVal = fresh("ctx")
    val companionExtra =
      q"""
        final val statusCode: Int = $statusCode

        def apply[..$methodTypeArgs](..$fieldsExceptHeaders, headers: com.hypertino.hyperbus.model.ResponseHeaders)
          :$className[..$classTypeNames] = {
          new $className[..$classTypeNames](..${fieldsExceptHeaders.map(_.name)},headers,
            plain__init = true
          )
        }

        def apply[..$methodTypeArgs](..$fieldsExceptHeaders, headers: com.hypertino.hyperbus.model.Headers)
         (implicit mcx: com.hypertino.hyperbus.model.MessagingContext)
         :$className[..$classTypeNames] = {
          new $className[..$classTypeNames](..${fieldsExceptHeaders.map(_.name)},
            headers = com.hypertino.hyperbus.model.ResponseHeaders(new com.hypertino.hyperbus.model.HeadersBuilder()
              .withStatusCode(statusCode)
              .withContentType(body.contentType)
              .withContext(mcx)
              .++=(headers)
              .result()),
            plain__init = true
          )
        }

        def apply[..$methodTypeArgs](..$fieldsExceptHeaders)
          (implicit mcx: com.hypertino.hyperbus.model.MessagingContext)
          : $className[..$classTypeNames]
          = apply(..${fieldsExceptHeaders.map(_.name)}, com.hypertino.hyperbus.model.Headers.empty)(mcx)

        ..$errorApply

        def unapply[..$methodTypeArgs](response: $className[..$classTypeNames]) = Some(
          (..${fieldsExceptHeaders.map(f ⇒ q"response.${f.name}")}, response.headers)
        )
    """

    val newCompanion = companionParts map { case (companion,companionBases,companionBody) =>
      q"""
          object $companion extends ..$companionBases {
            ..$companionBody
            ..$companionExtra
          }
        """
    } getOrElse {
      c.abort(c.enclosingPosition, s"Companion object for $className isn't found")
    }

    val block = q"""
        $newClass
        $newCompanion
      """

    // println(block)

    c.Expr(
      block
    )
  }
}