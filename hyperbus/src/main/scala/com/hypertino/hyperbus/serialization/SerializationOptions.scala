package com.hypertino.hyperbus.serialization

import com.hypertino.binders.core.BindOptions
import com.hypertino.binders.json.DefaultJsonBindersFactory
import com.hypertino.binders.value.DefaultValueSerializerFactory
import com.hypertino.inflector.naming.CamelCaseToSnakeCaseConverter

// todo: DefaultJsonBindersFactory[CamelCaseToSnakeCaseConverter.type] is fixed at compile time and can't be changed!
class SerializationOptions(aBindOptions: BindOptions,
                           aJsonBindersFactory: DefaultJsonBindersFactory[CamelCaseToSnakeCaseConverter.type],
                           aValueSerializerFactory: DefaultValueSerializerFactory[CamelCaseToSnakeCaseConverter.type]
                          ) {
  implicit val bindOptions: BindOptions = aBindOptions
  implicit val defaultJsonBindersFactory: DefaultJsonBindersFactory[CamelCaseToSnakeCaseConverter.type] = aJsonBindersFactory
  implicit val defaultValueBindersFactory: DefaultValueSerializerFactory[CamelCaseToSnakeCaseConverter.type] = aValueSerializerFactory

  def copy(bindOptions: BindOptions = this.bindOptions,
           jsonBindersFactory: DefaultJsonBindersFactory[CamelCaseToSnakeCaseConverter.type] = this.defaultJsonBindersFactory,
           valueSerializerFactory: DefaultValueSerializerFactory[CamelCaseToSnakeCaseConverter.type] = this.defaultValueBindersFactory
          ): SerializationOptions =
    new SerializationOptions(bindOptions, jsonBindersFactory, valueSerializerFactory)
}

object SerializationOptions {
  val caseConverter = CamelCaseToSnakeCaseConverter
  implicit val default: SerializationOptions = new SerializationOptions(
    BindOptions(skipOptionalFields=true),
    new DefaultJsonBindersFactory[com.hypertino.inflector.naming.CamelCaseToSnakeCaseConverter.type],
    new DefaultValueSerializerFactory[CamelCaseToSnakeCaseConverter.type]
    //new DefaultJsonBindersFactory[com.hypertino.inflector.naming.PlainConverter.type]
  )

  val forceOptionalFields: SerializationOptions = default.copy(
    bindOptions=BindOptions(skipOptionalFields=false)
  )
}
