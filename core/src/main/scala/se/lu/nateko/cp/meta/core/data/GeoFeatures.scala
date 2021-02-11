package se.lu.nateko.cp.meta.core.data

import java.text.DecimalFormat
import java.net.URI

sealed trait GeoFeature{
	def label: Option[String]
	def textSpecification: String
}

case class GeometryCollection(geometries: Seq[GeoFeature], label: Option[String]) extends GeoFeature {
	def textSpecification = geometries.map(_.textSpecification).mkString("Geometries: ", "; ", "")
}

case class Position(lat: Double, lon: Double, alt: Option[Float], label: Option[String]) extends GeoFeature{

	def textSpecification = s"Lat: $lat6, Lon: $lon6"

	def lat6 = PositionUtil.format6(lat)
	def lon6 = PositionUtil.format6(lon)
}

object PositionUtil{
	private val numForm = new DecimalFormat("###.######")
	def format6(d: Double): String = numForm.format(d).replace(',', '.')
}

case class LatLonBox(min: Position, max: Position, label: Option[String], uri: Option[URI]) extends GeoFeature{

	def asPolygon = Polygon(
		Seq(
			min, Position(lon = min.lon, lat = max.lat, alt = None, label = None),
			max, Position(lon = max.lon, lat = min.lat, alt = None, label = None),
		),
		label
	)

	def textSpecification = s"S: ${min.lat6}, W: ${min.lon6}, N: ${max.lat6}, E: ${max.lon6}"
}

case class GeoTrack(points: Seq[Position], label: Option[String]) extends GeoFeature{

	def textSpecification = points.map(p => s"(${p.textSpecification})").mkString("[", ", ", "]")

}

case class Polygon(vertices: Seq[Position], label: Option[String]) extends GeoFeature{

	def textSpecification = vertices.map(p => s"(${p.textSpecification})").mkString("[", ", ", "]")
}
