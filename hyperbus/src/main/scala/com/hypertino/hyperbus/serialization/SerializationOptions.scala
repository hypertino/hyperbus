package com.hypertino.hyperbus.serialization

import com.hypertino.binders.core.BindOptions
import com.hypertino.binders.json.DefaultJsonBindersFactory
import com.hypertino.inflector.naming.CamelCaseToSnakeCaseConverter

// todo: DefaultJsonBindersFactory[CamelCaseToSnakeCaseConverter.type] is fixed at compile time and can't be changed!
class SerializationOptions(aBindOptions: BindOptions, aJsonBindersFactory: DefaultJsonBindersFactory[CamelCaseToSnakeCaseConverter.type]) {
  implicit val bindOptions: BindOptions = aBindOptions
  implicit val defaultJsonBindersFactory: DefaultJsonBindersFactory[CamelCaseToSnakeCaseConverter.type] = aJsonBindersFactory

  def copy(bindOptions: BindOptions = this.bindOptions, jsonBindersFactory: DefaultJsonBindersFactory[CamelCaseToSnakeCaseConverter.type] = this.defaultJsonBindersFactory): SerializationOptions =
    new SerializationOptions(bindOptions, jsonBindersFactory)
}

object SerializationOptions {
  val caseConverter = CamelCaseToSnakeCaseConverter
  implicit val default: SerializationOptions = new SerializationOptions(
    BindOptions(skipOptionalFields=true),
    new DefaultJsonBindersFactory[com.hypertino.inflector.naming.CamelCaseToSnakeCaseConverter.type]
    //new DefaultJsonBindersFactory[com.hypertino.inflector.naming.PlainConverter.type]
  )

  val forceOptionalFields: SerializationOptions = default.copy(
    bindOptions=BindOptions(skipOptionalFields=false)
  )
}
