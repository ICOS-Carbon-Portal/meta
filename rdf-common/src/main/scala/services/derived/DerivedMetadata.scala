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

/** @param errors the errors that made the derivation fail, when the status is "failed" */
case class DerivedMetadataResult(resource: URI, status: String, metadata: Option[DerivedMetadata], errors: Seq[String] = Nil)
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
	given RootJsonFormat[DerivedMetadataResult] with
		private val base = jsonFormat4(DerivedMetadataResult.apply)
		def write(res: DerivedMetadataResult): JsValue = base.write(res)
		// rdfStores that predate the errors field do not report it
		def read(js: JsValue): DerivedMetadataResult = js match
			case JsObject(fields) if !fields.contains("errors") => base.read(JsObject(fields + ("errors" -> JsArray())))
			case _ => base.read(js)
	given RootJsonFormat[DerivedMetadataResponse] = jsonFormat2(DerivedMetadataResponse.apply)
