package se.lu.nateko.cp.meta.core

import java.net.URI
import java.time.Instant
import spray.json.*
import se.lu.nateko.cp.meta.core.data.TimeInterval
import java.time.{LocalDateTime, LocalDate}

trait CommonJsonSupport {

	given RootJsonFormat[URI] with{
		def write(uri: URI): JsValue = JsString(uri.toString)

		def read(value: JsValue): URI = value match{
			case JsString(uri) => try{
					new URI(uri)
				}catch{
					case err: Throwable => deserializationError(s"Could not parse URI from $uri", err)
				}
			case _ => deserializationError("URI string expected")
		}
	}

	given RootJsonFormat[LocalDateTime] with{

		def write(dt: LocalDateTime) = JsString(dt.toString)

		def read(value: JsValue): LocalDateTime = value match{
			case JsString(s) => LocalDateTime.parse(s)
			case _ => deserializationError("String representation of a LocalDateTime is expected")
		}
	}

	given RootJsonFormat[LocalDate] with{

		def write(dt: LocalDate) = JsString(dt.toString)

		def read(value: JsValue): LocalDate = value match{
			case JsString(s) => LocalDate.parse(s)
			case _ => deserializationError("String representation of a LocalDate is expected")
		}
	}

	given RootJsonFormat[Instant] with{

		def write(instant: Instant) = JsString(instant.toString)

		def read(value: JsValue): Instant = value match{
			case JsString(s) => Instant.parse(s)
			case _ => deserializationError("String representation of a time instant is expected")
		}
	}

	given JsonFormat[TimeInterval] = DefaultJsonProtocol.jsonFormat2(TimeInterval.apply)

	def enumFormat[T <: reflect.Enum](valueOf: String => T, values: Array[T]) = new RootJsonFormat[T] {
		def write(v: T) = JsString(v.toString)

		def read(value: JsValue): T = value match{
			case JsString(s) =>
				try{
					valueOf(s)
				}catch{
					case _: IllegalArgumentException => deserializationError(
						"Expected one of: " + values.mkString("'", "', '", "'")
					)
				}
			case _ => deserializationError("Expected a JSON string")
		}
	}

}

extension [T: RootJsonWriter](v: T){
	def toTypedJson(typ: String) = JsObject(
		v.toJson.asJsObject.fields + (CommonJsonSupport.TypeField -> JsString(typ))
	)
}

object CommonJsonSupport{
	val TypeField = "_type"
}
