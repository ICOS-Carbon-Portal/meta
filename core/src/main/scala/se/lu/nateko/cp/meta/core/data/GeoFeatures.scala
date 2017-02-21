package se.lu.nateko.cp.meta.core.data

sealed trait GeoFeature{
	def geoJson: String
	def textSpecification: String
}

object GeoFeature{
	def apply(geojson: String) = new GeoFeature{
		def geoJson = geojson
		def textSpecification = geojson
	}
}

case class Position(lat: Double, lon: Double) extends GeoFeature{
	def geoJson: String = s"""{
	|	"type": "Point",
	|	"coordinates": [$lon, $lat]
	}""".stripMargin

	def textSpecification = s"Lat: $lat, Lon: $lon"
}

case class SpatialCoverage(min: Position, max: Position, label: Option[String]) extends GeoFeature{
	def geoJson: String = s"""{
	|	"type": "Polygon",
	|	"coordinates": [
	|		[[${min.lon}, ${min.lat}], [${min.lon}, ${max.lat}], [${max.lon}, ${max.lat}], [${max.lon}, ${min.lat}], [${min.lon}, ${min.lat}]]
	|	]
	|}""".stripMargin

	def textSpecification = s"S: ${min.lat}, W: ${min.lon}, N: ${max.lat}, E: ${max.lon}"
}
