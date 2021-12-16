package se.lu.nateko.cp.meta.core

import java.net.URI
import java.time.Instant
import spray.json._
import se.lu.nateko.cp.meta.core.data.TimeInterval
import java.time.{LocalDateTime, LocalDate}

trait CommonJsonSupport extends DefaultJsonProtocol{common =>

	implicit object urlFormat extends RootJsonFormat[URI] {
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

	implicit object javaLocalDateTimeFormat extends RootJsonFormat[LocalDateTime] {

		def write(dt: LocalDateTime) = JsString(dt.toString)

		def read(value: JsValue): LocalDateTime = value match{
			case JsString(s) => LocalDateTime.parse(s)
			case _ => deserializationError("String representation of a LocalDateTime is expected")
		}
	}

	implicit object javaLocalDateFormat extends RootJsonFormat[LocalDate] {

		def write(dt: LocalDate) = JsString(dt.toString)

		def read(value: JsValue): LocalDate = value match{
			case JsString(s) => LocalDate.parse(s)
			case _ => deserializationError("String representation of a LocalDate is expected")
		}
	}

	implicit object javaTimeInstantFormat extends RootJsonFormat[Instant] {

		def write(instant: Instant) = JsString(instant.toString)

		def read(value: JsValue): Instant = value match{
			case JsString(s) => Instant.parse(s)
			case _ => deserializationError("String representation of a time instant is expected")
		}
	}

	implicit val timeIntervalFormat = jsonFormat2(TimeInterval)

	def enumFormat[T <: Enumeration](theEnum: T) = new RootJsonFormat[theEnum.Value] {
		def write(v: theEnum.Value) = JsString(v.toString)

		def read(value: JsValue): theEnum.Value = value match{
			case JsString(s) =>
				try{
					theEnum.withName(s)
				}catch{
					case _: NoSuchElementException => deserializationError(
						"Expected one of: " + theEnum.values.map(_.toString).mkString("'", "', '", "'")
					)
				}
			case _ => deserializationError("Expected a string")
		}
	}

}

object CommonJsonSupport extends CommonJsonSupport{
	val TypeField = "_type"
	implicit class TypeableSprayJsonableObject[T : RootJsonWriter](val v: T){
		def toTypedJson(typ: String) = JsObject(
			v.toJson.asJsObject.fields + (TypeField -> JsString(typ))
		)
	}

}
