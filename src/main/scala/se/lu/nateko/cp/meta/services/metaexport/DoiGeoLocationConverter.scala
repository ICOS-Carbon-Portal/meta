package se.lu.nateko.cp.meta.services.upload

import se.lu.nateko.cp.meta.core.data.GeoTrack.apply
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.doi.meta.GeoLocation
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.data.LatLonBox
import se.lu.nateko.cp.meta.core.data.FeatureCollection
import se.lu.nateko.cp.meta.core.data.GeoTrack
import se.lu.nateko.cp.meta.core.data.Polygon
import se.lu.nateko.cp.meta.core.data.Circle
import se.lu.nateko.cp.doi.meta.*
import se.lu.nateko.cp.meta.core.data.Pin

object DoiGeoLocationConverter {

	private def toLatLonBox(shape: Seq[Position], label: Option[String]) = {
		val latitudes = shape.map(_.lat)
		val longitudes = shape.map(_.lon)

		LatLonBox(
			Position(latitudes.min, longitudes.min, None, None),
			Position(latitudes.max, longitudes.max, None, None),
			label,
			None
		)
	}

	private def toLatLonBox(circle: Circle) = {
		val metersPerDegree = 111111
		val center = circle.center
		val latRadius = circle.radius / metersPerDegree
		val factor = Math.cos(center.lat.toRadians)

		val minLat = center.lat - latRadius
		val maxLat = center.lat + latRadius
		val minLon = center.lon - latRadius / factor
		val maxLon = center.lon + latRadius / factor

		LatLonBox(
			Position(minLat, minLon, center.alt, None),
			Position(maxLat, maxLon, center.alt, None),
			circle.label, 
			None
		)
	}

	private def toDoiGeoLocationWithPoint(pos: Position): GeoLocation =
		GeoLocation(Some(
			GeoLocationPoint(Some(Longitude(pos.lon)), Some(Latitude(pos.lat)))
		), None, pos.label)

	private def toDoiGeoLocationWithBox(box: LatLonBox): GeoLocation =
		GeoLocation(None, Some(
			GeoLocationBox(
				Some(Longitude(box.min.lon)), Some(Longitude(box.max.lon)), 
				Some(Latitude(box.min.lat)), Some(Latitude(box.max.lat))
			)), box.label
		)

	def toDoiGeoLocation(geoCoverage: GeoFeature): Seq[GeoLocation] =
		geoCoverage match {
			case p: Position => Seq(toDoiGeoLocationWithPoint(p))
			case Pin(position, _) => Seq(toDoiGeoLocationWithPoint(position))
			case b: LatLonBox => Seq(toDoiGeoLocationWithBox(b))
			case c: Circle => Seq(toDoiGeoLocationWithBox(toLatLonBox(c)))
			case GeoTrack(points, label) => Seq(toDoiGeoLocationWithBox(toLatLonBox(points, label)))
			case Polygon(vertices, label) => Seq(toDoiGeoLocationWithBox(toLatLonBox(vertices, label)))
			case fc: FeatureCollection => fc.features.flatMap(toDoiGeoLocation)
		}

}
