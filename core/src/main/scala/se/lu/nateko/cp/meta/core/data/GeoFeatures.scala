package se.lu.nateko.cp.meta.core.data

import java.text.DecimalFormat

sealed trait GeoFeature{
	def geoJson: String
	def textSpecification: String
}

case class GenericGeoFeature(val geoJson: String) extends GeoFeature{
	def textSpecification = geoJson
}

case class Position(lat: Double, lon: Double, alt: Option[Float]) extends GeoFeature{
	def geoJson: String = s"""{
	|	"type": "Point",
	|	"coordinates": [$lon6, $lat6]
	|}""".stripMargin

	def textSpecification = s"Lat: $lat6, Lon: $lon6"

	def lat6 = PositionUtil.format6(lat)
	def lon6 = PositionUtil.format6(lon)
}

object PositionUtil{
	private val numForm = new DecimalFormat("###.######")
	def format6(d: Double): String = numForm.format(d).replace(',', '.')
}

case class LatLonBox(min: Position, max: Position, label: Option[String]) extends GeoFeature{
	def geoJson: String = s"""{
	|	"type": "Polygon",
	|	"coordinates": [
	|		[[${min.lon6}, ${min.lat6}], [${min.lon6}, ${max.lat6}], [${max.lon6}, ${max.lat6}], [${max.lon6}, ${min.lat6}], [${min.lon6}, ${min.lat6}]]
	|	]
	|}""".stripMargin

	def textSpecification = s"S: ${min.lat6}, W: ${min.lon6}, N: ${max.lat6}, E: ${max.lon6}"
}

case class GeoTrack(points: Seq[Position]) extends GeoFeature{
	def geoJson: String = s"""{
		|	"type": "LineString",
		|	"coordinates": ${points.map(p => s"[${p.lon6}, ${p.lat6}]").mkString("[", ", ", "]")}
		|}""".stripMargin

	def textSpecification = points.map(p => s"(${p.textSpecification})").mkString("[", ", ", "]")

}

case class Polygon(vertices: Seq[Position]) extends GeoFeature{
	def geoJson: String = s"""{
		|	"type": "Polygon",
		|	"coordinates": [
		|		${(vertices ++ vertices.headOption).map(p => s"[${p.lon6}, ${p.lat6}]").mkString("[", ", ", "]")}
		|	]
		|}""".stripMargin

	def textSpecification = vertices.map(p => s"(${p.textSpecification})").mkString("[", ", ", "]")
}
