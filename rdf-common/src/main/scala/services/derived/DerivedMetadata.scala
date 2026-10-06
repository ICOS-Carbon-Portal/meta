package se.lu.nateko.cp.meta.services.derived

import se.lu.nateko.cp.meta.core.data.{Licence, References}
import se.lu.nateko.cp.meta.core.CommonJsonSupport
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import spray.json.*

import java.net.URI

case class DerivedMetadataRequest(resources: Seq[URI])

case class DerivedMetadata(
	resource: URI,
	references: References,
	citationString: Option[String],
	licence: Option[Licence],
	warnings: Seq[String] = Nil
)

case class DerivedMetadataResult(resource: URI, status: String, metadata: Option[DerivedMetadata])
case class DerivedMetadataResponse(version: Int, results: Seq[DerivedMetadataResult])

object DerivedMetadataJsonProtocol extends CommonJsonSupport:
	import DefaultJsonProtocol.*

	given RootJsonFormat[DerivedMetadataRequest] = jsonFormat1(DerivedMetadataRequest.apply)
	given RootJsonFormat[DerivedMetadata] = jsonFormat5(DerivedMetadata.apply)
	given RootJsonFormat[DerivedMetadataResult] = jsonFormat3(DerivedMetadataResult.apply)
	given RootJsonFormat[DerivedMetadataResponse] = jsonFormat2(DerivedMetadataResponse.apply)
