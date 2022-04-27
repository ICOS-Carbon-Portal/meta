package se.lu.nateko.cp.meta.core.sparql

import se.lu.nateko.cp.meta.core.CommonJsonSupport
import spray.json._
import DefaultJsonProtocol._

object JsonSupport extends CommonJsonSupport{

	given RootJsonFormat[BoundLiteral] = jsonFormat2(BoundLiteral.apply)
	given RootJsonFormat[BoundUri] = jsonFormat1(BoundUri.apply)

	given RootJsonFormat[BoundValue] with{
		def write(bv: BoundValue) = bv match{
			case uri: BoundUri => uri.toJson
			case lit: BoundLiteral => lit.toJson
		}

		def read(value: JsValue) = value match {
			case JsObject(fields) => fields.get("type") match {
				case Some(JsString("uri")) => value.convertTo[BoundUri]
				case Some(JsString("literal")) => value.convertTo[BoundLiteral]
				case _ => deserializationError("Expected a URI or a Literal")
			}
			case _ => deserializationError("JsObject expected")
		}
	}
	given RootJsonFormat[SparqlResultHead] = jsonFormat1(SparqlResultHead.apply)
	given RootJsonFormat[SparqlResultResults] = jsonFormat1(SparqlResultResults.apply)
	given RootJsonFormat[SparqlSelectResult] = jsonFormat2(SparqlSelectResult.apply)
}
