package se.lu.nateko.cp.meta.core.crypto

import spray.json._
import scala.util.Success
import scala.util.Failure

object JsonSupport extends DefaultJsonProtocol{

	implicit object sha256sumFormat extends RootJsonFormat[Sha256Sum]{
		import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

		def write(hash: Sha256Sum) = JsString(hash.base64Url)

		def read(value: JsValue): Sha256Sum = value match{
			case JsString(s) => Sha256Sum.fromString(s) match {
				case Success(hash) => hash
				case Failure(err) => deserializationError(err.getMessage, err)
			}
			case _ => deserializationError("Expected a string representation of an SHA-256 hashsum")
		}
	}
}
