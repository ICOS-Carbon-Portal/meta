package se.lu.nateko.cp.meta.core

import java.net.URI
import spray.json._

trait CommonJsonSupport extends DefaultJsonProtocol{

	implicit object uriFormat extends RootJsonFormat[URI] {
		def write(uri: URI) = JsString(uri.toString)
		def read(value: JsValue): URI = value match{
			case JsString(s) => new URI(s)
			case _ => deserializationError("String expected")
		}
	}

}