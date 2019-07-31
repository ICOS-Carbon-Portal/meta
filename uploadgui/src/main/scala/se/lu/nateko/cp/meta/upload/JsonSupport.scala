package se.lu.nateko.cp.meta.upload

import java.time.Instant

import play.api.libs.json._
import se.lu.nateko.cp.meta._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import java.net.URI

import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.doi._

object JsonSupport {

	implicit val positionWrites = Json.writes[Position]
	implicit val latLonBoxWrites = Json.writes[LatLonBox]

	implicit val instantWrites = new Writes[Instant]{
		def writes(i: Instant) = JsString(i.toString)
	}

	implicit val timeIntervalWrites = Json.writes[TimeInterval]
	implicit val temporalCoverageWrites = Json.writes[TemporalCoverage]

	implicit val uriWrites = new Writes[URI]{
		def writes(uri: URI) = JsString(uri.toASCIIString)
	}

	implicit val sha256SumWrites = new Writes[Sha256Sum]{
		def writes(hash: Sha256Sum) = JsString(hash.base64Url)
	}

	implicit val dataProductionDtoWrites = Json.writes[DataProductionDto]
	implicit def eitherWrites[L: Writes, R: Writes] = new Writes[Either[L, R]]{
		def writes(e: Either[L, R]) = e.fold(Json.toJson(_), Json.toJson(_))
	}

	implicit val elaboratedProductMetadataWrites = Json.writes[ElaboratedProductMetadata]
	implicit val stationDataMetadataWrites = Json.writes[StationDataMetadata]

	implicit val doiWrites = new Writes[Doi]{
		def writes(doi: Doi) = JsString(doi.toString)
	}

	implicit val staticCollectionDtoWrites = Json.writes[StaticCollectionDto]

	implicit val specificInfoWrites = new Writes[Either[ElaboratedProductMetadata, StationDataMetadata]]{
		def writes(info: Either[ElaboratedProductMetadata, StationDataMetadata]) = info.fold(Json.toJson(_), Json.toJson(_))
	}

	implicit val dataDtoWrites = Json.writes[DataObjectDto]
	implicit val documentDtoWrites = Json.writes[DocObjectDto]
	implicit val ObjectUploadDtoWrites = new Writes[ObjectUploadDto] {
		def writes(dto: ObjectUploadDto) = dto match {
			case dataObjectDto: DataObjectDto => Json.toJson(dataObjectDto)
			case documentObjectDto: DocObjectDto => Json.toJson(documentObjectDto)
		}
	}
	implicit val javaUriReads = new Reads[URI]{
		def reads(js: JsValue) = js.validate[String].map(new URI(_))
	}
	implicit val submitterProfileReads = Json.reads[SubmitterProfile]
	implicit val envriReads = new Reads[Envri]{
		def reads(js: JsValue) = js.validate[String].map(Envri.withName)
	}
	implicit val envriConfigReads = Json.reads[EnvriConfig]

}
