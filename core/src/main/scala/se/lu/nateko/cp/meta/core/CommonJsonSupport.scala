package se.lu.nateko.cp.meta.core

import java.net.URI
import java.time.Instant
import spray.json._

trait CommonJsonSupport extends DefaultJsonProtocol{

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

	implicit object javaTimeInstantFormat extends RootJsonFormat[Instant] {

		def write(instant: Instant) = JsString(instant.toString)

		def read(value: JsValue): Instant = value match{
			case JsString(s) => Instant.parse(s)
			case _ => deserializationError("String representation of a time instant is expected")
		}
	}

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
