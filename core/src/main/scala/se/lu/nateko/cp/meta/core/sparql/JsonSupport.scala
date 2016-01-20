package se.lu.nateko.cp.meta.core.sparql

import se.lu.nateko.cp.meta.core.CommonJsonSupport
import spray.json._

object JsonSupport extends CommonJsonSupport{

	implicit val boundLitFormat = jsonFormat2(BoundLiteral)
	implicit val boundUriFormat = jsonFormat1(BoundUri)

	implicit object boundValueFormat extends RootJsonFormat[BoundValue] {
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
	implicit val sparqlResultHeadFormat = jsonFormat1(SparqlResultHead)
	implicit val sparqlResultResultsFormat = jsonFormat1(SparqlResultResults)
	implicit val sparqlSelectResultFormat = jsonFormat2(SparqlSelectResult)
}
