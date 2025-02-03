package se.lu.nateko.cp.meta.services.metaexport

import se.lu.nateko.cp.doi.meta.{GeoLocation, *}
import se.lu.nateko.cp.meta.core.data.{Circle, FeatureCollection, FeatureWithGeoJson, GeoFeature, GeoTrack, LatLonBox, Pin, Polygon, Position}
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovMerger.circleToBox

object DoiGeoCovConverter:

	def fromGeoFeature(geoCoverage: GeoFeature): Seq[GeoLocation] =
		geoCoverage match
			case p: Position => Seq(fromPosition(p))
			case Pin(position, _) => Seq(fromPosition(position))
			case b: LatLonBox => Seq(fromBox(b))
			case c: Circle => Seq(fromBox(circleToBox(c)))
			case GeoTrack(points, label, _) => Seq(fromBox(toLatLonBox(points, label)))
			case p: Polygon => Seq(fromPolygon(p))
			case fc: FeatureCollection => fc.features.flatMap(fromGeoFeature)
			case FeatureWithGeoJson(feature, _)  => fromGeoFeature(feature)

	private def fromPolygon(polygon: Polygon): GeoLocation =
		// TODO Use the following when polygons are supported by DataCite REST API
		// GeoLocation(None, None, Some(
		// 	Seq(GeoLocationPolygon(
		// 		polygon.vertices.map(v => GeoLocationPoint(Some(Longitude(v.lon)), Some(Latitude(v.lat))))))
		// 	), polygon.label
		// )
		fromBox(toLatLonBox(polygon.vertices, polygon.label))

	private def toLatLonBox(shape: Seq[Position], label: Option[String]) = {
		val latitudes = shape.map(_.lat)
		val longitudes = shape.map(_.lon)

		LatLonBox(
			Position.ofLatLon(latitudes.min, longitudes.min),
			Position.ofLatLon(latitudes.max, longitudes.max),
			label,
			None
		)
	}

	private def fromPosition(pos: Position): GeoLocation =
		GeoLocation(Some(
			GeoLocationPoint(Some(Longitude(pos.lon)), Some(Latitude(pos.lat)))
		), None, pos.label)

	private def fromBox(box: LatLonBox): GeoLocation =
		GeoLocation(None, Some(
			GeoLocationBox(
				Some(Longitude(box.min.lon)), Some(Longitude(box.max.lon)), 
				Some(Latitude(box.min.lat)), Some(Latitude(box.max.lat))
			)), box.label
		)

end DoiGeoCovConverter
