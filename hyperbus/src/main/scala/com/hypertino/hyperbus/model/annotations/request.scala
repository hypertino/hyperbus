package com.hypertino.hyperbus.model.annotations

import com.hypertino.hyperbus.model.{Body, DefinedResponse, DynamicBodyTrait}

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
          extends ..$bases with scala.Product {
          ..$body

          def copy(
            ..${classFields.map { case ValDef(_, name, tpt, _) ⇒
              q"val $name: $tpt = this.$name"
            }}): $className = {
            new $className(..${classFields.map(_.name)}, plain__init = true)
          }

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
            ..${fmap._1}
         )(implicit mcx: com.hypertino.hyperbus.model.MessagingContext): $className =
         apply(..${fmap._2}, com.hypertino.hyperbus.model.HeadersMap.empty)(mcx)"""
    }

    val query = if (queryFields.isEmpty) {
      q"com.hypertino.binders.value.Null"
    }
    else {
      q"""
          com.hypertino.binders.value.Obj.from(..${queryFields.map(f ⇒ q"${f.name.decodedName.toString} -> ${f.name}")})
      """
    }

    val responses = getDefinedResponses(bases)
    val responseType =
      if (responses.size == 1)
        tq"${responses.head}"
      else
        tq"com.hypertino.hyperbus.model.ResponseBase"

    val responseDeserializerMethodBody = if (responses.nonEmpty) {
      val responseBodyTypes = getUniqueResponseBodies(responses)

      responseBodyTypes.groupBy(getContentTypeAnnotation(_) getOrElse "") foreach { kv =>
        if (kv._2.size > 1) {
          c.abort(c.enclosingPosition, s"Ambiguous responses for contentType: '${kv._1}': ${kv._2.mkString(",")}")
        }
      }

      val dynamicBodyTypeSig = typeOf[DynamicBodyTrait].typeSymbol.typeSignature
      val bodyCases: Seq[c.Tree] = responseBodyTypes.filterNot { t ⇒
        t.typeSymbol.typeSignature <:< dynamicBodyTypeSig
      } map { body =>
        val ta = getContentTypeAnnotation(body)
        val deserializer = body.companion.decl(TermName("apply"))
        //if (ta.isEmpty)
        //  c.abort(c.enclosingPosition, s"@contentType is not defined for $body")
        cq"""h: com.hypertino.hyperbus.model.ResponseHeaders if h.contentType == $ta => $deserializer(_: java.io.Reader, _: Option[String])"""
      }

      val r = q"""
        {
          val pf: PartialFunction[com.hypertino.hyperbus.model.ResponseHeaders, com.hypertino.hyperbus.serialization.ResponseBodyDeserializer] =
            (_: com.hypertino.hyperbus.model.ResponseHeaders) match { case ..$bodyCases }
          com.hypertino.hyperbus.model.StandardResponse.apply(_: java.io.Reader, _: com.hypertino.hyperbus.model.HeadersMap, pf)
        }
      """

      if (responses.size == 1) {
        q"$r.asInstanceOf[com.hypertino.hyperbus.serialization.ResponseDeserializer[${responses.head}]]"
      }
      else {
        r
      }
    }
    else {
      q"com.hypertino.hyperbus.model.StandardResponse.dynamicDeserializer"
    }

    val ctxVal = fresh("ctx")
    val bodyVal = fresh("body")
    val headersVal = fresh("headers")
    val argsVal = fresh("args")
    val hrlVal = fresh("hrl")
    val companionExtra =
      q"""
        type ResponseType = $responseType

        def apply(..$fieldsExceptHeaders,
          headersMap: com.hypertino.hyperbus.model.HeadersMap = com.hypertino.hyperbus.model.HeadersMap.empty)
          (implicit mcx: com.hypertino.hyperbus.model.MessagingContext): $className = {

          val $hrlVal = com.hypertino.hyperbus.model.HRL(${className.toTermName}.location, $query)

          new $className(..${fieldsExceptHeaders.map(_.name)},
            headers = com.hypertino.hyperbus.model.RequestHeaders(new com.hypertino.hyperbus.model.HeadersBuilder()
              .withHRL($hrlVal)
              .withMethod(${className.toTermName}.method)
              .withContentType(body.contentType)
              .withContext(mcx)
              .++=(headersMap)
              .result()),
            plain__init = true
          )
        }

        def apply(reader: java.io.Reader, headersMap: com.hypertino.hyperbus.model.HeadersMap): $className = {
          val $headersVal = com.hypertino.hyperbus.model.RequestHeaders(headersMap)
          val $bodyVal = ${bodyTypeName.toTermName}(reader, $headersVal.contentType)

          //todo: typed uri parts? int/long, etc
          //todo: uri part naming (fieldName?)
          //todo: names starting with _

          new $className(
            ..${
                queryFields.map { field ⇒
                q"${field.name} = $headersVal.hrl.query.${field.name}.to[${field.tpt}]"
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
        def responseDeserializer: com.hypertino.hyperbus.serialization.ResponseDeserializer[$responseType] = $responseDeserializerMethodBody
    """

    val newCompanion = clzCompanion map { existingCompanion =>
      val q"object $companion extends ..$bases { ..$body }" = existingCompanion
      q"""
          object $companion extends ..$bases {
            ..$body

            import com.hypertino.binders.value._
            ..$companionExtra
          }
        """
    } getOrElse {
      q"""
        object ${className.toTermName} extends com.hypertino.hyperbus.model.RequestMetaCompanion[${className.toTypeName}] {
          import com.hypertino.binders.value._
          ..$companionExtra
        }
      """
    }

    val block = c.Expr(
      q"""
        $newClass
        $newCompanion
      """
    )
    // println(block)
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

  private def getUniqueResponseBodies(definedResponses: Seq[Type]): Seq[c.Type] = {
    definedResponses.foldLeft(Seq[c.Type]())((seq, el) => {
      val bodyType = el.typeArgs.head
      if (!seq.exists(_ =:= bodyType)) {
        seq ++ Seq(el.typeArgs.head)
      }
      else
        seq
    })
  }
}