package se.lu.nateko.cp.meta.core.data

sealed trait GeoFeature{
	def geoJson: String
	def textSpecification: String
}

case class GenericGeoFeature(val geoJson: String) extends GeoFeature{
	def textSpecification = geoJson
}

case class Position(lat: Double, lon: Double) extends GeoFeature{
	def geoJson: String = s"""{
	|	"type": "Point",
	|	"coordinates": [$lon, $lat]
	|}""".stripMargin

	def textSpecification = s"Lat: $lat, Lon: $lon"
}

case class LatLonBox(min: Position, max: Position, label: Option[String]) extends GeoFeature{
	def geoJson: String = s"""{
	|	"type": "Polygon",
	|	"coordinates": [
	|		[[${min.lon}, ${min.lat}], [${min.lon}, ${max.lat}], [${max.lon}, ${max.lat}], [${max.lon}, ${min.lat}], [${min.lon}, ${min.lat}]]
	|	]
	|}""".stripMargin

	def textSpecification = s"S: ${min.lat}, W: ${min.lon}, N: ${max.lat}, E: ${max.lon}"
}

case class GeoTrack(points: Seq[Position]) extends GeoFeature{
	def geoJson: String = s"""{
		|	"type": "LineString",
		|	"coordinates": ${points.map(p => s"[${p.lon}, ${p.lat}]").mkString("[", ", ", "]")}
		|}""".stripMargin

	def textSpecification = points.map(p => s"(${p.textSpecification})").mkString("[", ", ", "]")

}
