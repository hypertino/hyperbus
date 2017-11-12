package com.hypertino.hyperbus.model.annotations

import com.hypertino.hyperbus.model.CollectionBody

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

@compileTimeOnly("enable macro paradise to expand macro annotations")
class body(v: String = "") extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro BodyMacroImpl.body
}

private[annotations] object BodyMacroImpl {
  def body(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with BodyAnnotationMacroImpl
    bundle.run(annottees)
  }
}

private[annotations] trait BodyAnnotationMacroImpl extends AnnotationMacroImplBase {

  import c.universe._

  def updateClass(existingClass: ClassDef, clzCompanion: Option[ModuleDef] = None): c.Expr[Any] = {
    val contentType = c.prefix.tree match {
      case q"new body($contentType)" => Some(contentType)
      case q"new body" => None
      case _ ⇒ c.abort(c.enclosingPosition, "@body annotation has invalid arguments")
    }

    val q"case class $className(..$fields) extends ..$bases { ..$body }" = existingClass

    val fVal = fresh("f")
    val serializerVal = fresh("serializer")
    val deserializerVal = fresh("deserializer")

    val collectionBodyTypeSig = typeOf[CollectionBody[_]].typeSymbol.typeSignature
    val collectionItemType = bases.flatMap { b ⇒
      val baseType = c.typecheck(q"(??? : $b)").tpe
      if (baseType.typeSymbol.typeSignature <:< collectionBodyTypeSig) {
        baseType.typeArgs.headOption
      }
      else {
        None
      }
    }.headOption

    val serializerMethod = if (collectionItemType.isDefined) {
      q"""
      override def serialize(writer: java.io.Writer)(implicit so: com.hypertino.hyperbus.serialization.SerializationOptions) = {
        import com.hypertino.binders.json.JsonBinders._
        import so._
        items.writeJson(writer)
      }
       """
    }
    else {
      q"""
      override def serialize(writer: java.io.Writer)(implicit so: com.hypertino.hyperbus.serialization.SerializationOptions) = {
        import com.hypertino.binders.json.JsonBinders._
        import so._
        this.writeJson(writer)
      }
       """
    }

    val newBodyContent = q"""
      ..$body
      def contentType = ${className.toTermName}.contentType
      $serializerMethod
    """

    val newClass = contentType match {
      case Some(ct) ⇒
        q"""
          @com.hypertino.hyperbus.model.annotations.contentType($ct) case class $className(..$fields) extends ..$bases with scala.Serializable {
            ..$newBodyContent
          }
        """
      case None ⇒
        q"""
          case class $className(..$fields) extends ..$bases with scala.Serializable {
            ..$newBodyContent
          }
        """
    }

    val deserializeMethod = collectionItemType match {
      case Some(typ) ⇒
        q"""
          def apply(reader: java.io.Reader, contentType: Option[String])
            (implicit so: com.hypertino.hyperbus.serialization.SerializationOptions): $className = {
            import com.hypertino.binders.json.JsonBinders._
            import so._
            ${className.toTermName}(reader.readJson[Seq[$typ]])
          }
        """

      case None ⇒
        q"""
          def apply(reader: java.io.Reader, contentType: Option[String])
            (implicit so: com.hypertino.hyperbus.serialization.SerializationOptions): $className = {
            import com.hypertino.binders.json.JsonBinders._
            import so._
            reader.readJson[$className]
          }
        """
    }

    // check requestHeader
    val companionExtra =
      q"""
        def contentType = $contentType
        $deserializeMethod
        """

    val newCompanion = clzCompanion map { existingCompanion =>
      val q"object $companion extends ..$bases { ..$body }" = existingCompanion
      q"""
          object $companion extends ..$bases {
            ..$body
            ..$companionExtra
          }
        """
    } getOrElse {
      q"""
        object ${className.toTermName} {
          ..$companionExtra
        }
      """
    }

    c.Expr(
      q"""
        $newClass
        $newCompanion
      """
    )
  }
}
