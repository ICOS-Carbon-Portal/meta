package se.lu.nateko.cp.meta.upload

import scala.language.unsafeNulls

import java.time.Instant

import play.api.libs.json.*
import se.lu.nateko.cp.meta.*
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import java.net.URI
import scala.reflect.ClassTag

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.doi.*

trait SealedTraitFormatPrecursor[T] extends OWrites[T]:
	def typedReads(js: JsValue, typeName: String): JsResult[T]


object JsonSupport:

	val TypeField = "_type"

	def sealedTraitFormat[T](precursor: SealedTraitFormatPrecursor[T], typeHint: String) = new OFormat[T]:
		def writes(t: T) = precursor.writes(t) + (TypeField -> JsString(t.getClass.getSimpleName))

		def reads(js: JsValue) = js match
			case JsObject(fields) =>
				fields.get(TypeField) match
					case Some(JsString(typeName)) => precursor.typedReads(js, typeName)
					case _ => JsError(s"Missing $TypeField field representing the type of $typeHint")
			case _ => JsError(s"Expected JsObject when parsing $typeHint, got ${js.toString}")
	end sealedTraitFormat

	given [T: Writes]: Writes[Seq[T]] = Writes.iterableWrites2
	
	given OFormat[Position] = Json.format[Position]
	given OFormat[LatLonBox] = Json.format[LatLonBox]
	given OFormat[Circle] = Json.format[Circle]
	given OFormat[GeoTrack] = Json.format[GeoTrack]
	given OFormat[Polygon] = Json.format[Polygon]
	given OFormat[Pin] = Json.format[Pin]
	given OFormat[FeatureWithGeoJson] = Json.format[FeatureWithGeoJson]
	given Format[PinKind] with
		def writes(pk: PinKind): JsValue = JsString(pk.toString)
		def reads(js: JsValue): JsResult[PinKind] = js.validate[String].map(PinKind.valueOf)


	private object geoFeatureFormatPrecursor extends SealedTraitFormatPrecursor[GeoFeature]:
		def writes(gf: GeoFeature) = gf match
			case box: LatLonBox            => Json.toJsObject(box)
			case pos: Position             => Json.toJsObject(pos)
			case c: Circle                 => Json.toJsObject(c)
			case geocol: FeatureCollection => Json.toJsObject(geocol)
			case gpoly: Polygon            => Json.toJsObject(gpoly)
			case p: Pin                    => Json.toJsObject(p)
			case gt: GeoTrack              => Json.toJsObject(gt)
			case jsgf: FeatureWithGeoJson  => Json.toJsObject(jsgf)

		def typedReads(js: JsValue, typeName: String) = typeName match
			case "Position"          => js.validate[Position]
			case "LatLonBox"         => js.validate[LatLonBox]
			case "Circle"            => js.validate[Circle]
			case "GeoTrack"          => js.validate[GeoTrack]
			case "FeatureCollection" => js.validate[FeatureCollection]
			case "Polygon"           => js.validate[Polygon]
			case "Pin"               => js.validate[Pin]
			case "FeatureWithGeoJson" => js.validate[FeatureWithGeoJson]
			case _ => JsError(s"Unexpected GeoFeature type $typeName")
	end geoFeatureFormatPrecursor

	given OFormat[GeoFeature] = sealedTraitFormat(geoFeatureFormatPrecursor, "GeoFeature")

	given OFormat[FeatureCollection] = Json.format[FeatureCollection]

	given Format[Instant] with
		def writes(i: Instant) = JsString(i.toString)
		def reads(js: JsValue) = js.validate[String].map(Instant.parse(_))

	given OFormat[TimeInterval] = Json.format[TimeInterval]
	given OFormat[TemporalCoverage] = Json.format[TemporalCoverage]

	given Format[GeoCoverage] with
		def writes(spatialObject: GeoCoverage) = spatialObject match
			case feature: GeoFeature => Json.toJson(feature)
			case uri: URI => Json.toJson(uri)
			case jsonString: GeoJsonString @unchecked => JsString(jsonString)
		def reads(json: JsValue) = json match
			case obj: JsObject => Json.fromJson[GeoFeature](obj)
			case str: JsString =>
				str.validate[URI].orElse(str.validate[String].map(GeoJsonString.unsafe))
			case _ => JsError(s"Error parsing GeoFeature from JSON ${json.getClass.getName}")

	given Format[Sha256Sum] with
		def writes(hash: Sha256Sum) = JsString(hash.base64Url)
		def reads(js: JsValue) = js.validate[String].map(Sha256Sum.fromString(_).get)

	given [L: Format, R: Format]: Format[Either[L, R]] with
		def writes(e: Either[L, R]) = e.fold(Json.toJson(_), Json.toJson(_))
		def reads(js: JsValue) = Json.fromJson[R](js).map(Right(_))
			.orElse(Json.fromJson[L](js).map(Left(_)))

	given OFormat[DataProductionDto] = Json.format[DataProductionDto]
	given OFormat[ReferencesDto] = Json.format[ReferencesDto]
	given OFormat[SpatioTemporalDto] = Json.format[SpatioTemporalDto]
	given OFormat[StationTimeSeriesDto] = Json.format[StationTimeSeriesDto]

	given Format[Doi] with
		def writes(doi: Doi) = JsString(doi.toString)
		def reads(js: JsValue) = js.validate[String].flatMap(s =>
			Doi.parse(s).fold(err => JsError(err.getMessage), doi => JsSuccess(doi))
		)

	given OFormat[DataObjectDto] = Json.format[DataObjectDto]
	given OFormat[DocObjectDto] = Json.format[DocObjectDto]
	given OFormat[StaticCollectionDto] = Json.format[StaticCollectionDto]

	given OFormat[UploadDto] with
		def writes(dto: UploadDto) = dto match
			case dataObjectDto: DataObjectDto             => Json.toJsObject(dataObjectDto)
			case documentObjectDto: DocObjectDto          => Json.toJsObject(documentObjectDto)
			case staticCollectionDto: StaticCollectionDto => Json.toJsObject(staticCollectionDto)

		def reads(js: JsValue) =
			val results = LazyList(
				Json.fromJson[DataObjectDto](js),
				Json.fromJson[DocObjectDto](js),
				Json.fromJson[StaticCollectionDto](js)
			)

			results.collectFirst { case JsSuccess(dto, _) => dto } match
				case Some(dto) => JsSuccess(dto)
				case None =>
					val errors = results.collect { case JsError(errors) => errors }.flatten
					JsError(errors)

	given Reads[SubmitterProfile] = Json.reads[SubmitterProfile]

	given Reads[Envri] with
		def reads(js: JsValue) = js.validate[String].map(Envri.valueOf)

	given Reads[EnvriConfig] = Json.reads[EnvriConfig]

end JsonSupport
