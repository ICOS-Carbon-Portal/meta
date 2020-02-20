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

	implicit val positionFormat = Json.format[Position]
	implicit val latLonBoxFormat = Json.format[LatLonBox]

	implicit val instantFormat = new Format[Instant]{
		def writes(i: Instant) = JsString(i.toString)
		def reads(js: JsValue) = js.validate[String].map(Instant.parse(_))
	}

	implicit val timeIntervalFormat = Json.format[TimeInterval]
	implicit val temporalCoverageFormat = Json.format[TemporalCoverage]

	implicit val uriFormat = new Format[URI]{
		def writes(uri: URI) = JsString(uri.toASCIIString)
		def reads(js: JsValue) = js.validate[String].map(new URI(_))
	}

	implicit val sha256SumFormat = new Format[Sha256Sum]{
		def writes(hash: Sha256Sum) = JsString(hash.base64Url)
		def reads(js: JsValue) = js.validate[String].map(Sha256Sum.fromString(_).get)
	}

	implicit val dataProductionDtoFormat = Json.format[DataProductionDto]
	implicit def eitherWrites[L: Writes, R: Writes] = new Writes[Either[L, R]]{
		def writes(e: Either[L, R]) = e.fold(Json.toJson(_), Json.toJson(_))
	}

	implicit def eitherReads[L: Reads, R: Reads] = new Reads[Either[L, R]]{
		def reads(js: JsValue) = Json.fromJson[R](js).map(Right(_))
			.orElse(Json.fromJson[L](js).map(Left(_)))
	}

	implicit val spatialReads = eitherReads[LatLonBox, URI]

	implicit val elaboratedProductMetadataFormat = Json.format[ElaboratedProductMetadata]
	implicit val stationDataMetadataFormat = Json.format[StationDataMetadata]

	implicit val doiFormat = new Format[Doi]{
		def writes(doi: Doi) = JsString(doi.toString)
		def reads(js: JsValue) = js.validate[String].flatMap(s =>
			Doi.parse(s).fold(err => JsError(err.getMessage), doi => JsSuccess(doi))
		)
	}

	implicit val specificInfoWrites = eitherWrites[ElaboratedProductMetadata, StationDataMetadata]
	implicit val specificInfoReads = eitherReads[ElaboratedProductMetadata, StationDataMetadata]

	implicit val dataDtoWrites = Json.writes[DataObjectDto]
	implicit val documentDtoWrites = Json.writes[DocObjectDto]
	implicit val staticCollectionDtoWrites = Json.writes[StaticCollectionDto]
	implicit val UploadDtoWrites = new Writes[UploadDto] {
		def writes(dto: UploadDto) = dto match {
			case dataObjectDto: DataObjectDto => Json.toJson(dataObjectDto)
			case documentObjectDto: DocObjectDto => Json.toJson(documentObjectDto)
			case staticCollectionDto: StaticCollectionDto => Json.toJson(staticCollectionDto)
		}
	}
	implicit val submitterProfileReads = Json.reads[SubmitterProfile]
	implicit val envriReads = new Reads[Envri]{
		def reads(js: JsValue) = js.validate[String].map(Envri.withName)
	}
	implicit val envriConfigReads = Json.reads[EnvriConfig]

	implicit val DataObjectDtoReads = Json.reads[DataObjectDto]
	implicit val DocObjectDtoReads = Json.reads[DocObjectDto]
	implicit val StaticCollectionDtoReads = Json.reads[StaticCollectionDto]
	implicit val UploadDtoReads = Reads[UploadDto] { js =>
		Json.fromJson[DataObjectDto](js)
			.orElse(Json.fromJson[DocObjectDto](js))
			.orElse(Json.fromJson[StaticCollectionDto](js))
	}


}
