package se.lu.nateko.cp.meta.core

import java.net.URI
import java.time.Instant
import spray.json._
import se.lu.nateko.cp.meta.core.data.TimeInterval
import java.time.LocalDateTime

trait CommonJsonSupport extends DefaultJsonProtocol{common =>

	//Working around issue https://github.com/spray/spray-json/issues/109
	implicit object CorrectedFloatJsonFormat extends JsonFormat[Float] {
		def write(x: Float) = JsNumber(x.toString.toDouble)
		def read(value: JsValue) = common.FloatJsonFormat.read(value)
	 }

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

	implicit object javaTimeInstantFormat extends RootJsonFormat[Instant] {

		def write(instant: Instant) = JsString(instant.toString)

		def read(value: JsValue): Instant = value match{
			case JsString(s) => Instant.parse(s)
			case _ => deserializationError("String representation of a time instant is expected")
		}
	}

	implicit val timeIntervalFormat = jsonFormat2(TimeInterval)

	def enumFormat[T <: Enumeration](enum: T) = new RootJsonFormat[enum.Value] {
		def write(v: enum.Value) = JsString(v.toString)

		def read(value: JsValue): enum.Value = value match{
			case JsString(s) =>
				try{
					enum.withName(s)
				}catch{
					case _: NoSuchElementException => deserializationError(
						"Expected one of: " + enum.values.map(_.toString).mkString("'", "', '", "'")
					)
				}
			case _ => deserializationError("Expected a string")
		}
	}

}

object CommonJsonSupport extends CommonJsonSupport
