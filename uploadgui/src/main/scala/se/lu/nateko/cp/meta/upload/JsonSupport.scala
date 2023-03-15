package se.lu.nateko.cp.meta.upload

import java.time.Instant

import play.api.libs.json.*
import se.lu.nateko.cp.meta.*
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import java.net.URI
import scala.reflect.ClassTag

import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.doi.*

object JsonSupport {

	given pos: OFormat[Position] = Json.format[Position]
	given OFormat[LatLonBox] = Json.format[LatLonBox]

	given Reads[Seq[Position]] = Reads.seq(pos)
	given Writes[Seq[Position]] = Writes.seq(pos)

	given OFormat[GeoTrack] = Json.format[GeoTrack]
	given OFormat[Polygon] = Json.format[Polygon]
	given OFormat[Circle] = Json.format[Circle]

	given Format[PinKind] = new Format[PinKind] {
		def reads(json: JsValue) = JsSuccess(PinKind.valueOf(json.as[String]))
		def writes(myEnum: PinKind) = JsString(myEnum.toString)
	}

	given OFormat[Pin] = Json.format[Pin]
	given OFormat[GeoFeature] = Json.format[GeoFeature]
	given OFormat[FeatureCollection] = Json.format[FeatureCollection]

	given Format[Instant] with{
		def writes(i: Instant) = JsString(i.toString)
		def reads(js: JsValue) = js.validate[String].map(Instant.parse(_))
	}

	given OFormat[TimeInterval] = Json.format[TimeInterval]
	given OFormat[TemporalCoverage] = Json.format[TemporalCoverage]

	given Format[URI] with{
		def writes(uri: URI) = JsString(uri.toASCIIString)
		def reads(js: JsValue) = js.validate[String].map(new URI(_))
	}

	given Format[Sha256Sum] with{
		def writes(hash: Sha256Sum) = JsString(hash.base64Url)
		def reads(js: JsValue) = js.validate[String].map(Sha256Sum.fromString(_).get)
	}

	given [L: Format, R: Format]: Format[Either[L, R]] with{
		def writes(e: Either[L, R]) = e.fold(Json.toJson(_), Json.toJson(_))
		def reads(js: JsValue) = Json.fromJson[R](js).map(Right(_))
			.orElse(Json.fromJson[L](js).map(Left(_)))
	}

	given OFormat[DataProductionDto] = Json.format[DataProductionDto]
	given OFormat[ReferencesDto] = Json.format[ReferencesDto]
	given OFormat[SpatioTemporalDto] = Json.format[SpatioTemporalDto]
	given OFormat[StationTimeSeriesDto] = Json.format[StationTimeSeriesDto]

	given Format[Doi] with{
		def writes(doi: Doi) = JsString(doi.toString)
		def reads(js: JsValue) = js.validate[String].flatMap(s =>
			Doi.parse(s).fold(err => JsError(err.getMessage), doi => JsSuccess(doi))
		)
	}

	given OFormat[DataObjectDto] = Json.format[DataObjectDto]
	given OFormat[DocObjectDto] = Json.format[DocObjectDto]
	given OFormat[StaticCollectionDto] = Json.format[StaticCollectionDto]

	given Format[UploadDto] with{
		def writes(dto: UploadDto) = dto match {
			case dataObjectDto: DataObjectDto => Json.toJson(dataObjectDto)
			case documentObjectDto: DocObjectDto => Json.toJson(documentObjectDto)
			case staticCollectionDto: StaticCollectionDto => Json.toJson(staticCollectionDto)
		}
		def reads(js: JsValue) = Json.fromJson[DataObjectDto](js)
			.orElse(Json.fromJson[DocObjectDto](js))
			.orElse(Json.fromJson[StaticCollectionDto](js))
	}

	given Reads[SubmitterProfile] = Json.reads[SubmitterProfile]

	given Reads[Envri] with{
		def reads(js: JsValue) = js.validate[String].map(Envri.valueOf)
	}

	given Reads[EnvriConfig] = Json.reads[EnvriConfig]

}
