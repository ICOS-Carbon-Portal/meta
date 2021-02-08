package se.lu.nateko.cp.meta.core.data

import spray.json.JsObject

import spray.json._

object GeoJson {

	def fromFeature(f: GeoFeature): JsObject = f match{

		case GeoTrack(points) => JsObject(
			"type"        -> JsString("LineString"),
			"coordinates" -> JsArray(points.map(coordinates).toVector)
		)

		case GeometryCollection(geometries) => JsObject(
			"type"       -> JsString("GeometryCollection"),
			"geometries" -> JsArray(geometries.map(fromFeature).toVector)
		)

		case p: Position => JsObject(
			"type"        -> JsString("Point"),
			"coordinates" -> coordinates(p)
		)

		case box: LatLonBox => fromFeature(box.asPolygon)

		case Polygon(vertices) => JsObject(
			"type"        -> JsString("Polygon"),
			"coordinates" -> JsArray(
				JsArray((vertices ++ vertices.headOption).map(coordinates).toVector)
			)
		)
	}

	def toFeature(geoJs: String): Option[GeoFeature] = scala.util.Try(geoJs.parseJson.asJsObject).toOption.flatMap(toFeature)

	def toFeature(json: JsObject): Option[GeoFeature] = {

		val coords = json.fields.get("coordinates")

		json.fields.get("type").collect{ case JsString(geoType) => geoType }.flatMap{

			case "Point" => coords.flatMap(parsePosition)

			case "LineString" => coords.flatMap(parsePointsArray).map(GeoTrack.apply)

			case "Polygon" => coords.flatMap{
				case JsArray(Vector(pntArr)) => parsePointsArray(pntArr).filter(_.size > 1).map{points =>
					Polygon(points.dropRight(1))
				}
				case _ => None
			}

			case "GeometryCollection" => json.fields.get("geometries").collect{
				case JsArray(elements) => GeometryCollection(
					elements.flatMap{
						case o: JsObject => toFeature(o)
						case _ => None
					}
				)
			}

			case _ => None
		}
	}

	private def parsePointsArray(geoJson: JsValue): Option[Vector[Position]] = geoJson match{
		case JsArray(elements) =>
			val points = elements.flatMap(parsePosition)
			if(points.isEmpty) None else Some(points)
		case _ => None
	}

	private def parsePosition(geoJson: JsValue): Option[Position] = geoJson match {
		case JsArray(Vector(JsNumber(lon), JsNumber(lat))) => Some(Position(lat.doubleValue, lon.doubleValue, None))
		case JsArray(Vector(JsNumber(lon), JsNumber(lat), JsNumber(elev))) => Some(Position(lat.doubleValue, lon.doubleValue, Some(elev.floatValue)))
		case _ => None
	}

	private def coordinates(p: Position) = {
		val latLon = Vector(JsNumber(p.lon6.toDouble), JsNumber(p.lat6.toDouble))
		val coords = p.alt.fold(latLon){alt => latLon :+ JsNumber(alt)}
		JsArray(coords)
	}
}
