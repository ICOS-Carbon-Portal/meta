package se.lu.nateko.cp.meta.core

import java.net.URI
import java.time.Instant
import spray.json.*
import se.lu.nateko.cp.meta.core.data.TimeInterval
import java.time.{LocalDateTime, LocalDate}
import java.net.URL

trait CommonJsonSupport:
	import CommonJsonSupport.*
	given uriFormat: RootJsonFormat[URI] with{
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

	given JsonFormat[URL] with{
		def write(uri: URL): JsValue = JsString(uri.toString)
		def read(value: JsValue): URL = uriFormat.read(value).toURL()
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

	export se.lu.nateko.cp.cpauth.core.JsonSupport.enumFormat
	// def enumFormat[T <: reflect.Enum](valueOf: String => T, values: Array[T]) = new RootJsonFormat[T] {
	// 	def write(v: T) = JsString(v.toString)

	// 	def read(value: JsValue): T = value match{
	// 		case JsString(s) =>
	// 			try{
	// 				valueOf(s)
	// 			}catch{
	// 				case _: IllegalArgumentException => deserializationError(
	// 					"Expected one of: " + values.mkString("'", "', '", "'")
	// 				)
	// 			}
	// 		case _ => deserializationError("Expected a JSON string")
	// 	}
	// }

	given [T] (using JsonWriter[T]): RootJsonFormat[WithErrors[T]] with
		def read(json: JsValue): WithErrors[T] = ??? // should not be needed
		def write(obj: WithErrors[T]): JsValue =
			val vanilla = obj.value.toJson.asJsObject
			if obj.errors.isEmpty then vanilla else JsObject(
				vanilla.fields + ("errors" -> JsArray(obj.errors.map(JsString(_))*))
			)

	extension (js: JsObject)
		def +(field: (String, JsValue)): JsObject =
			JsObject(js.fields + field)
	extension (js: JsValue)
		def pluss(field: (String, String)): JsObject =
			js.asJsObject + (field._1 -> JsString(field._2))

end CommonJsonSupport

extension [T: RootJsonWriter](v: T)
	def toTypedJson(typ: String): JsObject =
		import CommonJsonSupport.{pluss, TypeField}
		v.toJson.pluss(TypeField -> typ)


object CommonJsonSupport extends CommonJsonSupport:
	val TypeField = "_type"

	class WithErrors[T](val value: T, val errors: Seq[String])
