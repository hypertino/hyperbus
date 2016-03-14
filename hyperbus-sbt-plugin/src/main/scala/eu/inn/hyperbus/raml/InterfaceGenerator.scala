package eu.inn.hyperbus.raml

import java.util.Date

import com.mulesoft.raml1.java.parser.model.api.Api
import com.mulesoft.raml1.java.parser.model.datamodel.{DataElement, ObjectField, StrElement}
import com.mulesoft.raml1.java.parser.model.methodsAndResources.{Method, Resource}
import eu.inn.binders.naming._
import eu.inn.hyperbus.raml.annotationtypes.feed
import eu.inn.hyperbus.raml.utils.{DashCaseToUpperSnakeCaseConverter, DashCaseToPascalCaseConverter}
import eu.inn.hyperbus.transport.api.uri.{TextToken, UriParser}
import org.jibx.util.NameUtilities
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._

class InterfaceGenerator(api: Api, options: GeneratorOptions) {
  var log = LoggerFactory.getLogger(getClass)
  val dashToUpper = new DashCaseToUpperSnakeCaseConverter
  val dashToPascal = new DashCaseToPascalCaseConverter
  val camelToDash = new CamelCaseToDashCaseConverter

  def generate(): String = {
    val builder = new StringBuilder
    if (options.defaultImports) {
      generateImports(builder)
      builder.append("\n// -------------------- \n")
    }

    options.customImports.foreach { customImports ⇒
      builder.append(customImports)
      builder.append("\n// -------------------- \n")
    }

    if (options.generatorInformation) {
      generateInformation(builder)
      builder.append("\n// -------------------- \n")
    }
    generateTypes(builder)
    builder.append("\n// -------------------- \n")
    generateRequests(builder)
    builder.toString
  }

  protected def generateImports(builder: StringBuilder) = {
    builder.append(
      s"""
         |package ${options.packageName}
         |
         |import eu.inn.binders.annotations.fieldName
         |import eu.inn.hyperbus.model._
         |import eu.inn.hyperbus.model.annotations._
      """.stripMargin
    )
  }

  protected def generateInformation(builder: StringBuilder) = {
    builder.append(
      s"""
        |/*
        | DO NOT EDIT
        | Autogenerated on ${new Date}
        | options: $options
        |*/
        |
      """.stripMargin)
  }

  protected def generateTypes(builder: StringBuilder) = {
    api.types().foreach {
      case obj: ObjectField ⇒
        generateObjectType(builder, obj)

      case strEl: StrElement ⇒
        generateEnumStrElement(builder, strEl)

      case other ⇒
        log.warn(s"Currently $other is not supported in types")
    }
  }

  protected def generateObjectType(builder: StringBuilder, obj: ObjectField) = {
    val isBody = api.resources.exists { resource ⇒
      resource.methods.exists { method ⇒
        method.responses.exists { response ⇒
          response.body.exists { body ⇒
            body.`type`.contains(obj.name)
          }
        }
      }
    }

    if (isBody) {
      val getBodyResource = api.resources.find { resource ⇒
        resource.methods.exists { method ⇒
          method.method.toLowerCase == "get" &&
            method.responses.exists { response ⇒
              response.code.value == "200" &&
                response.body.exists { body ⇒
                  body.`type`.contains(obj.name)
                }
            }
        }
      }
      val isCreatedBody = api.resources.exists { resource ⇒
        resource.methods.exists { method ⇒
          method.responses.exists { response ⇒
            response.code.value == "201" &&
              response.body.exists { body ⇒
                body.`type`.contains(obj.name)
              }
          }
        }
      }

      builder.append(s"""@body("${options.contentTypePrefix.getOrElse("")}${camelToDash.convert(obj.name)}")\n""")
      builder.append(s"case class ${obj.name}(\n")
      generateCaseClassProperties(builder, obj.properties)
      if (isCreatedBody || getBodyResource.isDefined) {
        builder.append(s""",\n    @fieldName("_links") links: Links.LinksMap""")
        if (getBodyResource.isDefined) {
          builder.append(s""" = ${obj.name}.defaultLinks""")
        }
      }
      builder.append("\n  ) extends Body")
      if (isCreatedBody || getBodyResource.isDefined) {
        builder.append(" with Links")
      }
      if (isCreatedBody) {
        builder.append(" with CreatedBody")
      }
      builder.append("\n\n")

      getBodyResource.map { r ⇒
        builder.append(s"object ${obj.name}{\n")
        builder.append(s"""  val selfPattern = "${r.relativeUri.value}"\n""")
        builder.append(s"""  val defaultLinks = Links(selfPattern, templated = true)\n""")
        builder.append("}\n\n")
      }
    } else {
      builder.append(s"case class ${obj.name}(\n")
      generateCaseClassProperties(builder, obj.properties)
      builder.append("\n  )\n\n")
    }
  }

  protected def generateRequests(builder: StringBuilder) = {
    api.resources.foreach { resource ⇒
      resource.methods.foreach { method ⇒
        generateRequest(builder, method, resource)
        if (
          method.annotations().exists(_.value().isInstanceOf[feed])
          || resource.annotations().exists(_.value().isInstanceOf[feed])
        ) generateFeedRequest(builder, method, resource)
      }
    }
  }

  protected def generateRequest(builder: StringBuilder, method: Method, resource: Resource) = {
    builder.append(s"""@request(Method.${method.method.toUpperCase}, "${resource.relativeUri.value}")\n""")
    val name = requestClassName(resource.relativeUri.value, method.method)
    builder.append(s"case class $name(\n")
    val uriParameters = resource.uriParameters().toSeq
    generateCaseClassProperties(builder, uriParameters)
    val bodyType = method.method match {
      case "get" ⇒ "QueryBody"
      case "delete" ⇒ "EmptyBody"
      case _ ⇒
        method.body.headOption.flatMap(_.`type`.headOption).getOrElse("DynamicBody")
    }
    if (uriParameters.nonEmpty) {
      builder.append(",\n")
    }
    builder.append(s"    body: $bodyType\n  ) extends Request[$bodyType]\n")
    val successResponses = method.responses.filter{r ⇒ val i = r.code.value.toInt; i >= 200 && i < 400}
    if (successResponses.nonEmpty) {
      if (successResponses.size > 1)
        builder.append("  with DefinedResponse[(\n")
      else
        builder.append("  with DefinedResponse[\n")
      var isFirst = true
      successResponses.foreach { r ⇒
        if (isFirst) {
          builder.append("    ")
        }
        else {
          builder.append(",\n    ")
        }
        isFirst = false
        builder.append(getResponseType(r.code.value))
        builder.append('[')
        val responseBodyType = r.body.headOption.flatMap(_.`type`.headOption).getOrElse("DynamicBody")
        builder.append(responseBodyType)
        if (r.code.value == "201" && responseBodyType == "DynamicBody") {
          builder.append(" with CreatedBody")
        }
        builder.append(']')
      }
      if (successResponses.size > 1)
        builder.append("\n  )]\n\n")
      else
        builder.append("\n  ]\n\n")
    }
  }

  protected def generateFeedRequest(builder: StringBuilder, method: Method, resource: Resource) = {
    builder.append(s"""@request(Method.${"FEED_" + method.method.toUpperCase}, "${resource.relativeUri.value}")\n""")
    val name = requestClassName(resource.relativeUri.value, "feed-" + method.method)
    builder.append(s"case class $name(\n")
    val uriParameters = resource.uriParameters().toSeq
    generateCaseClassProperties(builder, uriParameters)
    val bodyType = method.method match {
      case "get" ⇒ "QueryBody"
      case "delete" ⇒ "EmptyBody"
      case _ ⇒
        method.body.headOption.flatMap(_.`type`.headOption).getOrElse("DynamicBody")
    }
    if (uriParameters.nonEmpty) {
      builder.append(",\n")
    }
    builder.append(s"    body: $bodyType\n  ) extends Request[$bodyType]\n\n")
  }

  protected def requestClassName(uriPattern: String, method: String): String = {
    val tokens = UriParser.tokens(uriPattern).zipWithIndex
    val last = tokens.reverse.head

    val dashed = tokens.collect {
      case (TextToken(s), index) ⇒
        // this is last token and it's text token, don't depluralize
        // it should be a collection
        if (index == last._2)
          s
        else
          NameUtilities.depluralize(s)
    } :+ method mkString "-"
    dashToPascal.convert(dashed)
  }

  protected def generateCaseClassProperties(builder: StringBuilder, properties: Seq[DataElement]) = {
    var isFirst = true
    properties.foreach { property ⇒
      if (isFirst) {
        builder.append("    ")
      }
      else {
        builder.append(",\n    ")
      }
      isFirst = false
      val propertyName = if (property.name.indexOf(':') >= 0) {
        // hyperbus syntax x:@, y:*, etc
        property.name.substring(0, property.name.indexOf(':'))
      } else {
        property.name
      }
      builder.append(propertyName)
      builder.append(": ")
      builder.append(property.`type`().headOption.map(mapType).getOrElse("Any"))
    }
  }

  protected def generateEnumStrElement(builder: StringBuilder, el: StrElement) = {
    builder.append(s"object ${el.name} {\n  type StringEnum = String\n")
    el.enum_.foreach { e ⇒
      builder.append(s"""  val ${dashToUpper.convert(e)} = "$e"\n""")
    }
    builder.append(s"  lazy val values = Seq(${el.enum_.map(dashToUpper.convert).mkString(",")})\n")
    builder.append("  lazy val valuesSet = values.toSet\n")
    builder.append("}\n\n")
  }

  protected def mapType(`type`: String): String = `type` match {
    case "string" ⇒ "String"
    case "integer" ⇒ "Long" // todo: support Int with annotations?
    case "number" ⇒ "Double"
    case "boolean" ⇒ "Boolean"
    case "date" ⇒ "java.util.Date"
    case other ⇒
      api.types.find(_.name == `type`) match {
        case Some(str: StrElement) ⇒
          if (str.enum_.nonEmpty) {
            other + ".StringEnum" // enum
          }
          else {
            other
          }
        case _ ⇒ other
      }
  }

  protected def getResponseType(code: String) = code match {
    case "200" ⇒ "Ok"
    case "201" => "Created"
    case "202" => "Accepted"
    case "203" => "NonAuthoritativeInformation"
    case "204" => "NoContent"
    case "205" => "ResetContent"
    case "206" => "PartialContent"
    case "207" => "MultiStatus"

    case "300" => "MultipleChoices"
    case "301" => "MovedPermanently"
    case "302" => "Found"
    case "303" => "SeeOther"
    case "304" => "NotModified"
    case "305" => "UseProxy"
    case "307" => "TemporaryRedirect"

    case _ ⇒ "???"
  }
}