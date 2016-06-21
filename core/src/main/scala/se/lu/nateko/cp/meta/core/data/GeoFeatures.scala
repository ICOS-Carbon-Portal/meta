package se.lu.nateko.cp.meta.core.data

trait GeoFeature{
	def geoJson: String
}

object GeoFeature{
	def apply(geojson: String) = new GeoFeature{
		def geoJson = geojson
	}
}

case class Position(lat: Double, lon: Double) extends GeoFeature{
	def geoJson: String = s"""{
	|	"type": "Point",
	|	"coordinates": [$lon, $lat]
	}""".stripMargin
}

case class SpatialCoverage(min: Position, max: Position, label: Option[String]) extends GeoFeature{
	def geoJson: String = s"""{
	|	"type": "Polygon",
	|	"coordinates": [
	|		[[${min.lon}, ${min.lat}], [${min.lon}, ${max.lat}], [${max.lon}, ${max.lat}], [${max.lon}, ${min.lat}], [${min.lon}, ${min.lat}]]
	|	]
	|}""".stripMargin
}
