package io.youi.util

import io.circe.parser._
import java.io.File

import io.circe.Json
import org.powerscala.io._
import profig.JsonUtil

object SwaggerClientBuilder {
  def main(args: Array[String]): Unit = {
    val file = new File("swagger.json")
    val json = parse(IO.stream(file, new StringBuilder).toString).getOrElse(throw new RuntimeException("Failed to parse!"))
    val directory = new File("utilities/src/main/scala/")
    val b = new SwaggerClientBuilder(directory, "com.outr.arango.api", json)
    b.createClasses()
  }
}

class SwaggerClientBuilder(directory: File, packageName: String, swagger: Json) {
  private val SnakeSplitter = "_?([a-zA-Z0-9]+)".r
  private val NameSplitter = "[-._ ]([a-zA-Z0-9])".r

  private val WordSwap = Map(
    "api" -> "API"
  )
  private val NameSwap = Map(
    "type" -> "`type`",
    "new" -> "`new`"
  )
  private val TypeSwap = Map(
    "boolean" -> "Boolean",
    "booleanboolean" -> "Boolean",
    "integer" -> "Int",
    "integerinteger" -> "Int",
    "integerint64" -> "Long",
    "integeruint64" -> "Long",
    "number" -> "Double",
    "numberfloat" -> "Double",
    "string" -> "String",
    "array" -> "List[String]",
    "arraystring" -> "List[String]",
    "arrayinteger" -> "List[Int]",
    "object" -> "io.circe.Json",
    "arrayobject" -> "List[io.circe.Json]"
  )
  private val definitions = (swagger \\ "definitions").head.asObject.get
  private val paths = (swagger \\ "paths").head
//  private val path = (swagger \\ "/_api/collection").head
  private val post = (swagger \\ "post").head
  private val parameters = (swagger \\ "parameters").head

  private val packageDirectory: File = new File(directory, packageName.replace('.', '/'))

  def createClasses(): List[String] = {
    // "post_api_collection_opts"
    definitions.toList.map {
      case (n, j) => {
        packageDirectory.mkdirs()
        val clazz = className(n)
        val source = createClass(clazz, j)
        val file = new File(packageDirectory, s"$clazz.scala")
        IO.stream(source, file)
        s"$packageName.$clazz"
      }
    }
  }

  def createClass(clazz: String, json: Json): String = {
    val required = (json \\ "required").headOption.map(_.asArray.get.flatMap(_.asString).toSet).getOrElse(Set.empty)
    val properties = (json \\ "properties").headOption.flatMap(_.asObject.map(_.toList)).getOrElse(Nil).map {
      case (n, j) => {
        (j \\ "$ref").headOption.flatMap(_.asString) match {
          case Some(ref) => {
            val refName = ref.substring(ref.lastIndexOf('/') + 1)
            Property(n, "", None, className(refName), required.contains(n), builtIn = false)
          }
          case None => {
            JsonUtil.fromJson[Property](j).copy(name = n, required = required.contains(n))
          }
        }
      }
    }.sortBy(!_.required)
    val pre = s"case class $clazz("
    val spacer = pre.replaceAll(".", " ")
    val params = properties.map(paramFrom)
    s"""package $packageName
       |
       |case class $clazz(${params.mkString(s",\n$spacer")})
     """.stripMargin.trim
  }

  def className(name: String): String = SnakeSplitter.replaceAllIn(name, m => {
    val value = m.group(1)
    WordSwap.getOrElse(value, value.capitalize)
  })

  def paramFrom(property: Property): String = {
    val name = NameSplitter.replaceAllIn(NameSwap.getOrElse(property.name, property.name), m => {
      m.group(1).toUpperCase
    }).replaceAll("[\\[\\]*]", "")
    val t = s"${property.`type`}${property.format.getOrElse("")}"
    val `type` = if (property.builtIn) {
      TypeSwap.getOrElse(t, throw new RuntimeException(s"Unsupported type: [$t] for ${property.name}"))
    } else {
      property.`type`
    }
    val finalType = if (property.required) {
      `type`
    } else {
      s"Option[${`type`}] = None"
    }
    s"$name: $finalType"
  }

  case class Property(name: String = "", description: String, format: Option[String], `type`: String, required: Boolean = false, builtIn: Boolean = true)
}