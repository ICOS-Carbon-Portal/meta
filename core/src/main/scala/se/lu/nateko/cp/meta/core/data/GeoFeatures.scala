package se.lu.nateko.cp.meta.core.data

import java.text.DecimalFormat
import java.net.URI
import spray.json._

sealed trait GeoFeature{
	def geoJson: String = geo.compactPrint
	def geo: JsObject //GeoJSON
	def textSpecification: String
}

// case class GenericGeoFeature(val geo: JsObject) extends GeoFeature{
// 	def textSpecification = geoJson
// }

case class GeometryCollection(geometries: Seq[GeoFeature]) extends GeoFeature {
	def geo = JsObject(
		"type"       -> JsString("GeometryCollection"),
		"geometries" -> JsArray(geometries.map(_.geo).toVector)
	)
	def textSpecification = geometries.map(_.textSpecification).mkString("Geometries: ", "; ", "")
}

case class Position(lat: Double, lon: Double, alt: Option[Float]) extends GeoFeature{

	def geo = JsObject(
		"type"        -> JsString("Point"),
		"coordinates" -> coordinates
	)

	def coordinates = {
		val latLon = Vector(JsNumber(lon6.toDouble), JsNumber(lat6.toDouble))
		val coords = alt.fold(latLon){alt => latLon :+ JsNumber(alt)}
		JsArray(coords)
	}

	def textSpecification = s"Lat: $lat6, Lon: $lon6"

	def lat6 = PositionUtil.format6(lat)
	def lon6 = PositionUtil.format6(lon)
}

object PositionUtil{
	private val numForm = new DecimalFormat("###.######")
	def format6(d: Double): String = numForm.format(d).replace(',', '.')
}

case class LatLonBox(min: Position, max: Position, label: Option[String], uri: Option[URI]) extends GeoFeature{

	def geo = Polygon(
		Seq(
			min, Position(lon = min.lon, lat = max.lat, alt = None),
			max, Position(lon = max.lon, lat = min.lat, alt = None),
			min
		)
	).geo

	def textSpecification = s"S: ${min.lat6}, W: ${min.lon6}, N: ${max.lat6}, E: ${max.lon6}"
}

case class GeoTrack(points: Seq[Position]) extends GeoFeature{

	def geo = JsObject(
		"type"        -> JsString("LineString"),
		"coordinates" -> JsArray(points.map(_.coordinates).toVector)
	)
	def textSpecification = points.map(p => s"(${p.textSpecification})").mkString("[", ", ", "]")

}

case class Polygon(vertices: Seq[Position]) extends GeoFeature{

	def geo = JsObject(
		"type"        -> JsString("Polygon"),
		"coordinates" -> JsArray(
			JsArray((vertices ++ vertices.headOption).map(_.coordinates).toVector)
		)
	)

	def textSpecification = vertices.map(p => s"(${p.textSpecification})").mkString("[", ", ", "]")
}

object GeoFeature{

	def parseGeoJson(geoJs: String) = scala.util.Try(geoJs.parseJson.asJsObject).toOption.flatMap(fromGeoJson)

	def fromGeoJson(json: JsObject): Option[GeoFeature] = {

		val coords = json.fields.get("coordinates")

		json.fields.get("type").collect{ case JsString(geoType) => geoType }.flatMap{

			case "Point" => coords.flatMap(parseLatLon)

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
						case o: JsObject => fromGeoJson(o)
						case _ => None
					}
				)
			}

			case _ => None
		}
	}

	private def parsePointsArray(geoJson: JsValue): Option[Seq[Position]] = geoJson match{
		case JsArray(elements) =>
			val points = elements.flatMap(parseLatLon)
			if(points.isEmpty) None else Some(points)
		case _ => None
	}

	private def parseLatLon(geoJson: JsValue): Option[Position] = geoJson match {
		case JsArray(Vector(JsNumber(lon), JsNumber(lat))) => Some(Position(lat.doubleValue, lon.doubleValue, None))
		case JsArray(Vector(JsNumber(lon), JsNumber(lat), JsNumber(elev))) => Some(Position(lat.doubleValue, lon.doubleValue, Some(elev.floatValue)))
		case _ => None
	}
}
