package se.lu.nateko.cp.meta.core.data

import spray.json._
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import scala.util.control.NoStackTrace

object GeoJson {

	class FormatException(msg: String) extends IllegalArgumentException(msg) with NoStackTrace

	def fromFeature(f: GeoFeature): JsObject = f match{

		case GeoTrack(points, _) => JsObject(
			"type"        -> JsString("LineString"),
			"coordinates" -> JsArray(points.map(coordinates).toVector)
		)

		case FeatureCollection(geometries, _) => JsObject(
			"type"       -> JsString("GeometryCollection"),
			"geometries" -> JsArray(geometries.map(fromFeature).toVector)
		)

		case p: Position => JsObject(
			"type"        -> JsString("Point"),
			"coordinates" -> coordinates(p)
		)

		case box: LatLonBox => fromFeature(box.asPolygon)

		case Polygon(vertices, _) => JsObject(
			"type"        -> JsString("Polygon"),
			"coordinates" -> JsArray(
				JsArray((vertices ++ vertices.headOption).map(coordinates).toVector)
			)
		)
	}

	private def toGeometryOrFeature(f: GeoFeature): Either[JsObject, JsObject] = f match{

		case GeoTrack(points, _) => Right(JsObject(
			"type"        -> JsString("LineString"),
			"coordinates" -> JsArray(points.map(coordinates).toVector)
		))

		case p: Position => Right(JsObject(
			"type"        -> JsString("Point"),
			"coordinates" -> coordinates(p)
		))

		case box: LatLonBox => toGeometryOrFeature(box.asPolygon)

		case Polygon(vertices, _) => Right(JsObject(
			"type"        -> JsString("Polygon"),
			"coordinates" -> JsArray(
				JsArray((vertices ++ vertices.headOption).map(coordinates).toVector)
			)
		))

		case FeatureCollection(features, _) =>
			val geomsOrFeats = features.map(toGeometryOrFeature).toVector
			if(geomsOrFeats.forall(_.isRight))
				Right(JsObject(
					"type"       -> JsString("GeometryCollection"),
					"geometries" -> JsArray(geomsOrFeats.flatMap(_.toOption))
				))
			else{
				val featuresJs: Vector[JsObject] = geomsOrFeats.zip(features).map{
					case (geoOrFeat, feat) => geoOrFeat.fold(
						geom => wrapGeoInFeature(geom, feat.label),
						identity
					)
				}
				Left(JsObject(
					"type"     -> JsString("FeatureCollection"),
					"features" -> JsArray(featuresJs)
				))
			}
	}

	private def wrapGeoInFeature(geo: JsObject, labelOpt: Option[String]) = JsObject(
		"type" -> JsString("Feature"),
		"geometry" -> geo,
		"properties" -> labelOpt.fold[JsValue](JsNull)(lbl => JsObject("label" -> JsString(lbl)))
	)

	def toFeature(geoJs: String): Try[GeoFeature] =
		Try(geoJs.parseJson.asJsObject).flatMap(toFeature(_))

	def toFeature(json: JsObject): Try[GeoFeature] = {

		def field(name: String): Try[JsValue] = json.fields.get(name).fold[Try[JsValue]](
				fail(s"'$name' not found in ${json.compactPrint}")
			)(Success.apply)

		def coords = field("coordinates")

		field("type").collect{ case JsString(geoType) => geoType }.flatMap{

			case "Point" => coords.flatMap(parsePosition(_))

			case "LineString" => coords.flatMap(parsePointsArray).map(GeoTrack(_, None))

			case "Polygon" => coords.map{
				case JsArray(Vector(pntArr)) => {
					val points = parsePointsArray(pntArr).get
					if(points.size < 2) throw new FormatException(s"Expected polygon, got ${points.size} points: ${pntArr.compactPrint}")
					else Polygon(points.dropRight(1), None)
				}
				case other =>
					throw new FormatException(s"Expected polygon coordinates to be a single-element JsArray, got ${other.compactPrint}")
			}

			case "GeometryCollection" => field("geometries").collect{
				case JsArray(elements) => FeatureCollection(
					elements.map{
						case o: JsObject => toFeature(o).get
						case other =>
							throw new FormatException(s"Expected JsObject, got ${other.compactPrint}")
					},
					None
				)
				case other =>
					throw new FormatException(s"Expected 'geometries' to be a JsArray, got ${other.compactPrint}")
			}

			case "FeatureCollection" => ???

			case "Feature" => ???

			case other => fail(s"Unsupported GeoJSON feature type: $other")
		}
	}

	private def parsePointsArray(geoJson: JsValue): Try[Vector[Position]] = geoJson match{
		case JsArray(elements) => Try{
			elements.map(p => parsePosition(p).get)
		}
		case _ =>
			fail(s"Expected JSON array, got ${geoJson.compactPrint}")
	}

	private def parsePosition(geoJson: JsValue): Try[Position] = geoJson match {
		case JsArray(Vector(JsNumber(lon), JsNumber(lat))) =>
			Success(Position(lat.doubleValue, lon.doubleValue, None, None))
		case JsArray(Vector(JsNumber(lon), JsNumber(lat), JsNumber(elev))) =>
			Success(Position(lat.doubleValue, lon.doubleValue, Some(elev.floatValue), None))
		case _ =>
			fail(s"Not a valid JSON for GeoJSON for a position: ${geoJson.compactPrint}")
	}

	private def coordinates(p: Position) = {
		val latLon = Vector(JsNumber(p.lon6.toDouble), JsNumber(p.lat6.toDouble))
		val coords = p.alt.fold(latLon){alt => latLon :+ JsNumber(alt)}
		JsArray(coords)
	}

	private def fail(msg: String) = Failure(new FormatException(msg))
}
