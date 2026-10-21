package se.lu.nateko.cp.meta.services.derived

import se.lu.nateko.cp.meta.core.data.{Licence, References}
import se.lu.nateko.cp.meta.core.CommonJsonSupport
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.services.citation.RemoteCitation
import spray.json.*

import java.net.URI

case class DerivedMetadataRequest(resources: Seq[URI])

case class DerivedMetadata(
	resource: URI,
	references: References,
	citationString: Option[String],
	licence: Option[Licence],
	warnings: Seq[String] = Nil,
	/** None when produced by an rdfStore that does not report the remote citation status */
	remoteCitation: Option[RemoteCitation] = None
)

case class DerivedMetadataResult(resource: URI, status: String, metadata: Option[DerivedMetadata])
case class DerivedMetadataResponse(version: Int, results: Seq[DerivedMetadataResult])

object DerivedMetadataJsonProtocol extends CommonJsonSupport:
	import DefaultJsonProtocol.*

	given RootJsonFormat[DerivedMetadataRequest] = jsonFormat1(DerivedMetadataRequest.apply)
	given RootJsonFormat[RemoteCitation] with
		def write(rc: RemoteCitation): JsValue = rc match
			case RemoteCitation.NotApplicable => JsObject("expected" -> JsFalse)
			case RemoteCitation.Expected(error) => JsObject(
				Map("expected" -> JsTrue) ++ error.map(err => "error" -> JsString(err))
			)
		def read(js: JsValue): RemoteCitation = js.asJsObject.fields.get("expected") match
			case Some(JsTrue) => RemoteCitation.Expected(js.asJsObject.fields.get("error").map(_.convertTo[String]))
			case Some(JsFalse) => RemoteCitation.NotApplicable
			case _ => deserializationError(s"Expected remote citation status, got $js")

	given RootJsonFormat[DerivedMetadata] = jsonFormat6(DerivedMetadata.apply)
	given RootJsonFormat[DerivedMetadataResult] = jsonFormat3(DerivedMetadataResult.apply)
	given RootJsonFormat[DerivedMetadataResponse] = jsonFormat2(DerivedMetadataResponse.apply)
