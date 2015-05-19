package se.lu.nateko.cp.meta

import spray.httpx.SprayJsonSupport
import spray.json._
import java.net.URI
import java.net.URISyntaxException

case class ResourceInfo(displayName: String, uri: URI)

object CpmetaJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport{

	implicit object UriJsonFormat extends RootJsonFormat[URI]{

		def read(value: JsValue) = value match{
			case JsString(uri) => try{
					new URI(uri)
				}catch{
					case err: Throwable => deserializationError(s"Could not parse URI from$uri", err)
				}
			case _ => deserializationError("URI string expected")
		}

		def write(uri: URI) = JsString(uri.toString)
	}

	implicit val resourceInfoFormat = jsonFormat2(ResourceInfo)

}
