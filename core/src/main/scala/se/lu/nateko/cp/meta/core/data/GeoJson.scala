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

		case GeometryCollection(geometries, _) => JsObject(
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

	def toFeature(geoJs: String, labelOpt: Option[String]): Try[GeoFeature] =
		Try(geoJs.parseJson.asJsObject).flatMap(toFeature(_, labelOpt))

	def toFeature(json: JsObject, labelOpt: Option[String]): Try[GeoFeature] = {

		def field(name: String): Try[JsValue] = json.fields.get(name).fold[Try[JsValue]](
				fail(s"'$name' not found in ${json.compactPrint}")
			)(Success.apply)

		def coords = field("coordinates")

		field("type").collect{ case JsString(geoType) => geoType }.flatMap{

			case "Point" => coords.flatMap(parsePosition(_, labelOpt))

			case "LineString" => coords.flatMap(parsePointsArray).map(GeoTrack(_, labelOpt))

			case "Polygon" => coords.map{
				case JsArray(Vector(pntArr)) => {
					val points = parsePointsArray(pntArr).get
					if(points.size < 2) throw new FormatException(s"Expected polygon, got ${points.size} points: ${pntArr.compactPrint}")
					else Polygon(points.dropRight(1), labelOpt)
				}
				case other =>
					throw new FormatException(s"Expected polygon coordinates to be a single-element JsArray, got ${other.compactPrint}")
			}

			case "GeometryCollection" => field("geometries").collect{
				case JsArray(elements) => GeometryCollection(
					elements.map{
						case o: JsObject => toFeature(o, labelOpt).get
						case other =>
							throw new FormatException(s"Expected JsObject, got ${other.compactPrint}")
					},
					labelOpt
				)
				case other =>
					throw new FormatException(s"Expected 'geometries' to be a JsArray, got ${other.compactPrint}")
			}

			case other => fail(s"Unsupported GeoJSON feature type: $other")
		}
	}

	private def parsePointsArray(geoJson: JsValue): Try[Vector[Position]] = geoJson match{
		case JsArray(elements) => Try{
			elements.map(p => parsePosition(p, None).get)
		}
		case _ =>
			fail(s"Expected JSON array, got ${geoJson.compactPrint}")
	}

	private def parsePosition(geoJson: JsValue, labelOpt: Option[String]): Try[Position] = geoJson match {
		case JsArray(Vector(JsNumber(lon), JsNumber(lat))) =>
			Success(Position(lat.doubleValue, lon.doubleValue, None, labelOpt))
		case JsArray(Vector(JsNumber(lon), JsNumber(lat), JsNumber(elev))) =>
			Success(Position(lat.doubleValue, lon.doubleValue, Some(elev.floatValue), labelOpt))
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
