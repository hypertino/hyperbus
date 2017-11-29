/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.hyperbus.model.annotations

import com.hypertino.hyperbus.model.{Body, DefinedResponse, DynamicBody, EmptyBody}
import com.hypertino.hyperbus.serialization.SerializationOptions

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.api.Trees
import scala.reflect.macros.blackbox.Context
import scala.util.matching.Regex

@compileTimeOnly("enable macro paradise to expand macro annotations")
class request(method: String, uri: String) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro RequestMacro.request
}

private[annotations] object RequestMacro {
  def request(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with RequestAnnotationMacroImpl
    bundle.run(annottees)
  }
}

private[annotations] trait RequestAnnotationMacroImpl extends AnnotationMacroImplBase {
  val c: Context

  import c.universe._

  def getCommonResponseType(responses: Seq[Type]): Tree = {
    if (responses.isEmpty || responses.exists(_.typeArgs.isEmpty)) {
      tq"com.hypertino.hyperbus.model.ResponseBase"
    }
    else if (responses.nonEmpty && responses.tail.isEmpty) {
      tq"${responses.head}"
    }
    else {
      val t = responses.head
      val headBodyType = t.typeArgs.head
      if (responses.tail.forall(_.typeArgs.head == headBodyType)) {
        tq"com.hypertino.hyperbus.model.Response[$headBodyType]"
      }
      else {
        tq"com.hypertino.hyperbus.model.ResponseBase"
      }
    }
  }

  def updateClass(existingClass: ClassDef, clzCompanion: Option[ModuleDef] = None): c.Expr[Any] = {
    val (method, location) = c.prefix.tree match {
      case q"new request($method, $location)" => {
        (c.Expr(method), c.eval[String](c.Expr(location)))
      }
      case _ ⇒ c.abort(c.enclosingPosition, "Please provide arguments for @request annotation")
    }

    val q"case class $className(..$fields) extends ..$bases { ..$body }" = existingClass

    val classFields: Seq[ValDef] = if (fields.exists(_.name.toString == "headers")) fields else {
      fields :+ q"val headers: com.hypertino.hyperbus.model.RequestHeaders"
    }

    val (bodyFieldName, bodyTypeName, bodyType) = getBodyField(fields)
    val contentType = getContentTypeAnnotation(bodyType)

    val equalExpr = classFields.map(_.name).foldLeft[Tree](q"true") { (cap, name) ⇒
      q"(o.$name == this.$name) && $cap"
    }

    val cases = classFields.map(_.name).zipWithIndex.map { case (name, idx) ⇒
      cq"$idx => this.$name"
    }

    val fieldsExceptHeaders = classFields.filterNot(_.name.toString == "headers")
    val queryFields = fieldsExceptHeaders.filterNot(_.name.decodedName == bodyFieldName.decodedName)

    val newClass =
      q"""
        @com.hypertino.hyperbus.model.annotations.location($location)
        @com.hypertino.hyperbus.model.annotations.method($method)
        class $className(..$classFields, plain__init: Boolean = false)
          extends ..$bases with scala.Product with scala.Serializable {
          ..$body

          def copy(
            ..${classFields.map { case ValDef(_, name, tpt, _) ⇒
              q"val $name: $tpt = this.$name"
            }}): $className = {
            new $className(..${classFields.map(_.name)}, plain__init = true)
          }

          override def copyWithHeaders(headers: Headers) = this.copy(
              headers=MessageHeaders
                .builder
                .++=(this.headers)
                .++=(headers)
                .requestHeaders()
            )

          def canEqual(other: Any): Boolean = other.isInstanceOf[$className]

          override def equals(other: Any) = this.eq(other.asInstanceOf[AnyRef]) ||{
            other match {
              case o @ ${className.toTermName}(
                ..${classFields.map(f ⇒ q"${f.name}")}
              ) if $equalExpr ⇒ other.asInstanceOf[$className].canEqual(this)
              case _ => false
            }
          }

          override def hashCode: Int = scala.runtime.ScalaRunTime._hashCode(this)
          override def productArity: Int = ${classFields.size}
          override def productElement(n: Int): Any = n match {
            case ..$cases
            case _ => throw new IndexOutOfBoundsException(n.toString())
          }
        }
      """

    val fieldsWithDefVal = fieldsExceptHeaders.filter(_.rhs.nonEmpty) :+
      q"val headers: com.hypertino.hyperbus.model.RequestHeaders = com.hypertino.hyperbus.model.RequestHeaders()(mcx)".asInstanceOf[ValDef]


    val defMethods = fieldsWithDefVal.map { currentField: ValDef ⇒
      val fmap = fieldsExceptHeaders.foldLeft((Seq.empty[Tree], Seq.empty[Tree], false)) { case ((seqFields, seqVals, withDefaultValue), f) ⇒
        val defV = withDefaultValue || f.name == currentField.name

        (seqFields ++ {if (!defV) Seq(stripDefaultValue(f)) else Seq.empty},
        seqVals :+ {if (defV) q"${f.name} = ${f.rhs}" else  q"${f.name}"},
          defV)
      }
      //val name = TermName(if(fmap._1.isEmpty) "em" else "apply")
      q"""def apply(
            ..${fmap._1},
            query: com.hypertino.binders.value.Value = com.hypertino.binders.value.Null
         )(implicit mcx: com.hypertino.hyperbus.model.MessagingContext): $className =
         apply(..${fmap._2}, com.hypertino.hyperbus.model.Headers.empty, query)(mcx)"""
    }

    val query = if (queryFields.isEmpty) {
      q"com.hypertino.binders.value.Obj.empty"
    }
    else {
      q"""
          com.hypertino.binders.value.Obj.from(..${queryFields.map(f ⇒ q"${SerializationOptions.caseConverter.convert(f.name.decodedName.toString)} -> ${f.name}.toValue")})
      """
    }

    val responses = getDefinedResponses(bases)
    val commonResponseType = getCommonResponseType(responses)

    val responseDeserializerMethodBody = if (responses.nonEmpty) {
      val bodyCases: Seq[c.Tree] = responses.map { response ⇒
        val body = response.typeArgs.head
        val ta = getContentTypeAnnotation(body)
        val deserializer = body.companion.decl(TermName("apply"))
        cq"""
             h: com.hypertino.hyperbus.model.ResponseHeaders if h.statusCode == ${response.companion.typeSymbol.asClass.name.toTermName}.statusCode &&
                h.contentType.map(ct => $ta.map(_ == ct).getOrElse(true)).getOrElse(true) =>
                $deserializer(_: java.io.Reader, _: Option[String])
          """
      }

      val r = q"""
        {
          val pf: PartialFunction[com.hypertino.hyperbus.model.ResponseHeaders, com.hypertino.hyperbus.serialization.ResponseBodyDeserializer] =
            (_: com.hypertino.hyperbus.model.ResponseHeaders) match { case ..$bodyCases }
          com.hypertino.hyperbus.model.StandardResponse.apply(_: java.io.Reader, _: com.hypertino.hyperbus.model.Headers, pf, true)
        }
      """

      q"$r.asInstanceOf[com.hypertino.hyperbus.serialization.ResponseDeserializer[$commonResponseType]]"
    }
    else {
      q"com.hypertino.hyperbus.model.StandardResponse.dynamicDeserializer"
    }

    val ctxVal = fresh("ctx")
    val bodyVal = fresh("body")
    val headersVal = fresh("headers")
    val argsVal = fresh("args")
    val hrlVal = fresh("hrl")
    def companionExtra(defaults: Boolean) = {
      val headerFields = if (defaults) {
        Seq(
          q"val headers: com.hypertino.hyperbus.model.Headers = com.hypertino.hyperbus.model.Headers.empty",
          q"val query: com.hypertino.binders.value.Value = com.hypertino.binders.value.Obj.empty"
        )
      } else {
        Seq(
          q"val headers: com.hypertino.hyperbus.model.Headers",
          q"val query: com.hypertino.binders.value.Value"
        )
      }

      q"""
        def apply(..$fieldsExceptHeaders, ..$headerFields)
          (implicit mcx: com.hypertino.hyperbus.model.MessagingContext): $className = {

          val $hrlVal = com.hypertino.hyperbus.model.HRL(${className.toTermName}.location, query % $query)

          new $className(..${fieldsExceptHeaders.map(_.name)},
            headers = com.hypertino.hyperbus.model.RequestHeaders(new com.hypertino.hyperbus.model.HeadersBuilder()
              .++=(headers)
              .withHRL($hrlVal)
              .withMethod(${className.toTermName}.method)
              .withContentType(body.contentType)
              .withContext(mcx)
              .result()),
            plain__init = true
          )
        }

        def apply(reader: java.io.Reader, headers: com.hypertino.hyperbus.model.Headers): $className = {
          val $headersVal = com.hypertino.hyperbus.model.RequestHeaders(headers)
          val $bodyVal = ${bodyTypeName.toTermName}(reader, $headersVal.contentType)

          //todo: typed uri parts? int/long, etc
          //todo: uri part naming (fieldName?)
          //todo: names starting with _

          new $className(
            ..${
              queryFields.map { field ⇒
                q"${field.name} = $headersVal.hrl.query.dynamic.${TermName(SerializationOptions.caseConverter.convert(field.name.decodedName.toString))}.to[${field.tpt}]"
              }
            },
            $bodyFieldName = $bodyVal,
            headers = $headersVal,
            plain__init = true
          )
        }

        def unapply(request: $className) = Some((
          ..${classFields.map(f ⇒ q"request.${f.name}")}
        ))

        def location: String = $location
        def method: String = $method
        def contentType: Option[String] = $contentType
        def requestMatcher: com.hypertino.hyperbus.transport.api.matchers.RequestMatcher = com.hypertino.hyperbus.transport.api.matchers.RequestMatcher(
          location,
          method,
          contentType
        )
        def responseDeserializer: com.hypertino.hyperbus.serialization.ResponseDeserializer[$commonResponseType] = $responseDeserializerMethodBody
      """
    }

    val newCompanion = clzCompanion map { existingCompanion =>
      val q"object $companion extends ..$bases { ..$body }" = existingCompanion
      q"""
          object $companion extends ..$bases {
            ..$body

            import com.hypertino.binders.value._

            ..${companionExtra(false)}
          }
        """
    } getOrElse {
      q"""
        object ${className.toTermName} extends com.hypertino.hyperbus.model.RequestMetaCompanion[${className.toTypeName}] {
          import com.hypertino.binders.value._

          type ResponseType = $commonResponseType
          implicit val meta = this

          ..${companionExtra(true)}
        }
      """
    }

    val block = c.Expr(
      q"""
        $newClass
        $newCompanion
      """
    )
    //println(block)
    block
  }

  def stripDefaultValue(field: ValDef): ValDef = ValDef(field.mods, field.name, field.tpt, EmptyTree)

  def getBodyField(fields: Seq[Trees#ValDef]): (TermName, TypeName, Type) = {
    fields.flatMap { field ⇒
      field.tpt match {
        case i: Ident ⇒
          val typeName = i.name.toTypeName
          val fieldType = c.typecheck(q"(??? : $typeName)").tpe
          if (fieldType <:< typeOf[Body]) {
            Some((field.name.asInstanceOf[TermName], typeName, fieldType))
          }
          else
            None
        case _ ⇒
          None
      }
    }.headOption.getOrElse {
      c.abort(c.enclosingPosition, "No Body parameter was found")
    }
  }

  // todo: test this
  private def getDefinedResponses(baseClasses: Seq[Tree]): Seq[Type] = {
    val tDefined = typeOf[DefinedResponse[_]]
    //.typeSymbol.typeSignature
    val tupleRegex = new Regex("^Tuple(\\d+)$")

    baseClasses
      .map(t ⇒ {
        c.typecheck(q"(??? : $t)").tpe
      })
      .find(_ <:< tDefined)
      .map { definedType: Type ⇒
        // DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])] expected
        if (definedType.typeArgs.headOption.exists(h ⇒ tupleRegex.findFirstIn(h.typeSymbol.name.toString).isDefined)) {
          definedType.typeArgs.head.typeArgs
        }
        else {
          // DefinedResponse[Ok[DynamicBody]] expected
          definedType.typeArgs
        }
      }
      .getOrElse(Seq.empty)
  }

  private def getContentTypeAnnotation(t: c.Type): Option[String] = {
    getStringAnnotation(t, c.typeOf[contentType])
  }

  private def getStringAnnotation(t: c.Type, atype: c.Type): Option[String] = {
    val typeChecked = c.typecheck(q"(??? : ${t.typeSymbol})").tpe
    val symbol = typeChecked.typeSymbol

    symbol.annotations.find { a =>
      a.tree.tpe <:< atype
    } flatMap {
      annotation => annotation.tree.children.tail.head match {
        case Literal(Constant(s: String)) => Some(s)
        case _ => None
      }
    }
  }

//  private def getUniqueResponseBodies(definedResponses: Seq[Type]): Seq[c.Type] = {
//    definedResponses.foldLeft(Seq[c.Type]())((seq, el) => {
//      val bodyType = el.typeArgs.head
//      if (!seq.exists(_ =:= bodyType)) {
//        seq ++ Seq(el.typeArgs.head)
//      }
//      else
//        seq
//    })
//  }
}