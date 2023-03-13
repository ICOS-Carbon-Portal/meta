package se.lu.nateko.cp.meta.core.data

import java.text.DecimalFormat
import java.net.URI

sealed trait GeoFeature{
	protected type Self >: this.type <: GeoFeature
	def label: Option[String]
	def withOptLabel(label: Option[String]): Self
	def textSpecification: String
	def withLabel(label: String): Self = withOptLabel(Some(label))
}

type LatLon = (Double, Double)

case class FeatureCollection(features: Seq[GeoFeature], label: Option[String]) extends GeoFeature {
	type Self = FeatureCollection
	def textSpecification = features.map(_.textSpecification).mkString("Geometries: ", "; ", "")

	def flatten = {
		def flattenFeature(f: GeoFeature): Seq[GeoFeature] = f match{
			case FeatureCollection(geometries, _) => geometries.flatMap(flattenFeature)
			case _ => Seq(f)
		}
		copy(features = features.flatMap(flattenFeature))
	}

	def withOptLabel(label: Option[String]) = copy(label = label)
}

case class Position(lat: Double, lon: Double, alt: Option[Float], label: Option[String]) extends GeoFeature{
	type Self = Position
	def latlon: LatLon = lat -> lon

	def textSpecification = s"Lat: $lat6, Lon: $lon6" + alt.fold("")(alt => s", Alt: $alt m")

	def lat6 = PositionUtil.format6(lat)
	def lon6 = PositionUtil.format6(lon)

	def withOptLabel(label: Option[String]) = copy(label = label)
}

object PositionUtil:
	private val numForm = new DecimalFormat("###.######")
	def format6(d: Double): String = numForm.format(d).replace(',', '.')
	def average(ps: Iterable[Position]): Option[Position] = {
		var latSum, lonSum: Double = 0
		var n: Int = 0
		var heightSum: Float = 0
		var nHeight: Int = 0
		ps.foreach{p =>
			n += 1
			latSum += p.lat
			lonSum += p.lon
			p.alt.foreach{height =>
				nHeight += 1
				heightSum += height
			}
		}
		if(n == 0) None else Some(Position(
			lat = latSum / n,
			lon = lonSum / n,
			alt = (if(nHeight == 0) None else Some(heightSum / nHeight)),
			label = None
		))
	}

	def distanceInMeters(p1: LatLon, p2: LatLon): Float =
		val x = (p2._2 - p1._2) * Math.cos(Math.toRadians(p1._1 + p2._1) / 2)
		val y = (p2._1 - p1._1)
		val R = 6371000
		val degreeLength = Math.PI * R / 180

		(Math.sqrt(x * x + y * y) * degreeLength).toFloat

	def posClusterLookup(pos: Iterable[Position], toleranceMeters: Float): Map[LatLon, LatLon] =
		pos.foldLeft(Map.empty){(lookup, p) =>
			val latLon = p.latlon
			if lookup.contains(latLon) then lookup else
				val neighbour = lookup.valuesIterator
					.minByOption(distanceInMeters(latLon, _))
					.filter(distanceInMeters(_,latLon) < toleranceMeters)
				lookup + (latLon -> neighbour.getOrElse(latLon))
		}

end PositionUtil

case class LatLonBox(min: Position, max: Position, label: Option[String], uri: Option[URI]) extends GeoFeature{
	type Self = LatLonBox

	def asPolygon = Polygon(
		Seq(
			min, Position(lon = min.lon, lat = max.lat, alt = None, label = None),
			max, Position(lon = max.lon, lat = min.lat, alt = None, label = None),
		),
		label
	)

	def textSpecification = s"S: ${min.lat6}, W: ${min.lon6}, N: ${max.lat6}, E: ${max.lon6}"

	def withOptLabel(label: Option[String]) = copy(label = label)
}

case class GeoTrack(points: Seq[Position], label: Option[String]) extends GeoFeature{
	type Self = GeoTrack

	def textSpecification = points.map(p => s"(${p.textSpecification})").mkString("[", ", ", "]")

	def withOptLabel(label: Option[String]) = copy(label = label)

}

case class Polygon(vertices: Seq[Position], label: Option[String]) extends GeoFeature{
	type Self = Polygon

	def textSpecification = vertices.map(p => s"(${p.textSpecification})").mkString("[", ", ", "]")

	def withOptLabel(label: Option[String]) = copy(label = label)
}

case class Circle(center: Position, radius: Float, label: Option[String]) extends GeoFeature{
	type Self = Circle

	def textSpecification: String = s"(${center.textSpecification}, Rad: $radius m)"

	def withOptLabel(label: Option[String]) = copy(label = label)
}

enum PinKind:
	case Sensor, Other

case class Pin(position: Position, kind: PinKind) extends GeoFeature:
	type Self = Pin
	export position.label
	def textSpecification: String = s"Pin ($kind): ${position.textSpecification}"
	def withOptLabel(label: Option[String]) = copy(position = position.withOptLabel(label))
