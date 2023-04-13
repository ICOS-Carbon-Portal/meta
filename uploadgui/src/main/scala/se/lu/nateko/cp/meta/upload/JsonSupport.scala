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

	val TypeField = "_type"

	given [T: Writes]: Writes[Seq[T]] = Writes.iterableWrites2
	
	given OFormat[Position] = Json.format[Position]
	given OFormat[LatLonBox] = Json.format[LatLonBox]
	given OFormat[Circle] = Json.format[Circle]
	given OFormat[GeoTrack] = Json.format[GeoTrack]
	given OFormat[Polygon] = Json.format[Polygon]
	given OFormat[Pin] = Json.format[Pin]
	given Format[PinKind] with
		def writes(pk: PinKind): JsValue = JsString(pk.toString)
		def reads(js: JsValue): JsResult[PinKind] = js.validate[String].map(PinKind.valueOf)


	private object vanillaGeoFeatureFormat extends OFormat[GeoFeature]:
		def getAllFields(base: JsObject, geo: GeoFeature): JsObject =
			val allFields = base.fields ++ Seq(TypeField -> JsString(geo.getClass.getName))
			JsObject(allFields)

		def writes(gf: GeoFeature) = gf match
			case box: LatLonBox => getAllFields(Json.toJsObject(box), box)
			case pos: Position => getAllFields(Json.toJsObject(pos), pos)
			case c: Circle => getAllFields(Json.toJsObject(c), c)
			case geocol: FeatureCollection => getAllFields(Json.toJsObject(geocol), geocol)
			case gpoly: Polygon => getAllFields(Json.toJsObject(gpoly), gpoly)
			case p: Pin => getAllFields(Json.toJsObject(p), p)
			case gt: GeoTrack => getAllFields(Json.toJsObject(gt), gt)

		def reads(js: JsValue) = js match
			case JsObject(fields) =>
				fields.get(TypeField) match
					case Some(JsString(typeName)) => typeName match
						case "se.lu.nateko.cp.meta.core.data.Position" => js.validate[Position]
						case "se.lu.nateko.cp.meta.core.data.LatLonBox" => js.validate[LatLonBox]
						case "se.lu.nateko.cp.meta.core.data.Circle" => js.validate[Circle]
						case "se.lu.nateko.cp.meta.core.data.GeoTrack" => js.validate[GeoTrack]
						case "se.lu.nateko.cp.meta.core.data.FeatureCollection" => js.validate[FeatureCollection]
						case "se.lu.nateko.cp.meta.core.data.Polygon" => js.validate[Polygon]
						case "se.lu.nateko.cp.meta.core.data.Pin" => js.validate[Pin]
						case _ => JsError(s"Unexpected GeoFeature type $typeName")	
					case _ => JsError("Expected a 'type' property representing the type of GeoFeature")
			case _ => JsError("Expected a JsObject representing a GeoFeature")
	end vanillaGeoFeatureFormat

	given OFormat[GeoFeature] with
		def writes(geo: GeoFeature) =
			val base = vanillaGeoFeatureFormat.writes(geo)
			val allFields = base.fields ++ Seq(TypeField -> JsString(geo.getClass.getName))
			JsObject(allFields)

		def reads(value: JsValue) = vanillaGeoFeatureFormat.reads(value)

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
		def writes(dto: UploadDto) = dto match
			case dataObjectDto: DataObjectDto => 
				val fields = Json.toJsObject(dataObjectDto).fields ++ Seq(TypeField -> JsString(dataObjectDto.getClass.getName))
				JsObject(fields)
			case documentObjectDto: DocObjectDto => 
				val fields = Json.toJsObject(documentObjectDto).fields ++ Seq(TypeField -> JsString(documentObjectDto.getClass.getName))
				JsObject(fields)
			case staticCollectionDto: StaticCollectionDto =>
				val fields = Json.toJsObject(staticCollectionDto).fields ++ Seq(TypeField -> JsString(staticCollectionDto.getClass.getName))
				JsObject(fields)

		def reads(js: JsValue) = js match
			case JsObject(fields) =>
				fields.get(TypeField) match
					case Some(JsString(typeName)) => typeName match
						case "se.lu.nateko.cp.meta.DataObjectDto" => Json.fromJson[DataObjectDto](js)
						case "se.lu.nateko.cp.meta.DocObjectDto" => Json.fromJson[DocObjectDto](js)
						case "se.lu.nateko.cp.meta.StaticCollectionDto" => Json.fromJson[StaticCollectionDto](js)
						case _ => JsError(s"Unexpected GeoFeature type $typeName")
					case _ => JsError("Expected a 'type' property representing the type of GeoFeature")
			case _ => JsError("Expected a JsObject representing a GeoFeature")
	}

	given Reads[SubmitterProfile] = Json.reads[SubmitterProfile]

	given Reads[Envri] with{
		def reads(js: JsValue) = js.validate[String].map(Envri.valueOf)
	}

	given Reads[EnvriConfig] = Json.reads[EnvriConfig]

}
