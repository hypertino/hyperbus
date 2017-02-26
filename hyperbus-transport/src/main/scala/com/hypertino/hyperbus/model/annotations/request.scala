package com.hypertino.hyperbus.model.annotations

import com.hypertino.hyperbus.model.Body

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.api.Trees
import scala.reflect.macros.blackbox.Context

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
    val (method, serviceAddress) = c.prefix.tree match {
      case q"new request($method, $serviceAddress)" => {
        (c.Expr(method), c.eval[String](c.Expr(serviceAddress)))
      }
      case _ ⇒ c.abort(c.enclosingPosition, "Please provide arguments for @request annotation")
    }

    val q"case class $className(..$fields) extends ..$bases { ..$body }" = existingClass

    val classFields: Seq[ValDef] = if (fields.exists(_.name.toString == "headers")) fields else {
      fields :+ q"val headers: com.hypertino.hyperbus.model.RequestHeaders"
    }

    val (bodyFieldName, bodyType) = getBodyField(fields)
    //println(s"rhs = $defaultValue, ${defaultValue.isEmpty}")

    val equalExpr = classFields.map(_.name).foldLeft[Tree](q"true") { (cap, name) ⇒
      q"(o.$name == this.$name) && $cap"
    }

    val cases = classFields.map(_.name).zipWithIndex.map { case (name, idx) ⇒
      cq"$idx => this.$name"
    }

    val fieldsExceptHeaders = classFields.filterNot(_.name.toString == "headers").map { field: ValDef ⇒
      val ft = getFieldType(field)
      // todo: the following is hack. due to compiler restriction, defval can't be provided as def field arg
      // it's also possible to explore field-type if it has a default constructor, companion with apply ?
      val rhs = ft.toString match {
        case "com.hypertino.hyperbus.model.EmptyBody" ⇒ q"com.hypertino.hyperbus.model.EmptyBody"
        case other => field.rhs
      }
      ValDef(field.mods, field.name, field.tpt, rhs)
    }

    val queryFields = fieldsExceptHeaders.filterNot(_.name.decodedName == bodyFieldName.decodedName)

    val newClass =
      q"""
        @com.hypertino.hyperbus.model.annotations.serviceAddress($serviceAddress)
        @com.hypertino.hyperbus.model.annotations.method($method)
        class $className(..${classFields.map(stripDefaultValue)}, plain__init: Boolean)
          extends ..$bases with scala.Product {
          ..$body

          def copy(
            ..${classFields.map { case ValDef(_, name, tpt, _) ⇒
              q"val $name: $tpt = this.$name"
            }}): $className = {
            new $className(..${fieldsExceptHeaders.map(_.name)}, headers = this.headers, plain__init = false)
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
          com.hypertino.binders.value.ObjV(..${queryFields.map(f ⇒ q"${f.name.decodedName.toString} -> ${f.name}")})
      """
    }

    val ctxVal = fresh("ctx")
    val bodyVal = fresh("body")
    val headersVal = fresh("headers")
    val argsVal = fresh("args")
    val hriVal = fresh("hri")
    val companionExtra =
      q"""
        def apply(..${fieldsExceptHeaders.map(stripDefaultValue)}, headersMap: com.hypertino.hyperbus.model.HeadersMap)
          (implicit mcx: com.hypertino.hyperbus.model.MessagingContext): $className = {

          val $hriVal = com.hypertino.hyperbus.model.HRI(${className.toTermName}.serviceAddress, $query)

          new $className(..${fieldsExceptHeaders.map(_.name)},
            headers = com.hypertino.hyperbus.model.RequestHeaders(new com.hypertino.hyperbus.model.HeadersBuilder(headersMap)
              .withHRI($hriVal)
              .withMethod(${className.toTermName}.method)
              .withContentType(body.contentType)
              .withContext(mcx)
              .result()),
            plain__init = false
          )
        }

        ..$defMethods

        def apply(reader: java.io.Reader, headersMap: com.hypertino.hyperbus.model.HeadersMap): $className = {
          val $headersVal = com.hypertino.hyperbus.model.RequestHeaders(headersMap)
          val $bodyVal = ${bodyType.toTermName}(reader, $headersVal.contentType)

          //todo: typed uri parts? int/long, etc
          //todo: uri part naming (fieldName?)
          //todo: names starting with _

          new $className(
            ..${
                queryFields.map { field ⇒
                q"${field.name} = $headersVal.hri.query.${field.name}.to[${field.tpt}]"
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

        def serviceAddress: String = $serviceAddress
        def method: String = $method
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
        object ${className.toTermName} extends com.hypertino.hyperbus.model.RequestObjectApi[${className.toTypeName}] {
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
    println(block)
    block
  }

  def stripDefaultValue(field: ValDef): ValDef = ValDef(field.mods, field.name, field.tpt, EmptyTree)

  def getBodyField(fields: Seq[Trees#ValDef]): (TermName, TypeName) = {
    fields.flatMap { field ⇒
      field.tpt match {
        case i: Ident ⇒
          val typeName = i.name.toTypeName
          val fieldType = c.typecheck(q"(??? : $typeName)").tpe
          if (fieldType <:< typeOf[Body]) {
            Some((field.name.asInstanceOf[TermName], typeName))
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

  def getFieldType(field: Trees#ValDef): Type = field.tpt match {
    case i: Ident ⇒
      val typeName = i.name.toTypeName
      c.typecheck(q"(??? : $typeName)").tpe
  }
}