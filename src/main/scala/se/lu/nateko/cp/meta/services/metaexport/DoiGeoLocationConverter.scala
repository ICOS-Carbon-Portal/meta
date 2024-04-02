package se.lu.nateko.cp.meta.services.upload

import org.locationtech.jts.algorithm.hull.ConcaveHull
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.LineString as JtsLineString
import org.locationtech.jts.geom.Point as JtsPoint
import org.locationtech.jts.geom.Polygon as JtsPolygon
import org.locationtech.jts.io.geojson.GeoJsonReader
import se.lu.nateko.cp.doi.meta.GeoLocation
import se.lu.nateko.cp.doi.meta.*
import se.lu.nateko.cp.meta.core.data.Circle
import se.lu.nateko.cp.meta.core.data.FeatureCollection
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.GeoJson
import se.lu.nateko.cp.meta.core.data.GeoTrack
import se.lu.nateko.cp.meta.core.data.GeoTrack.apply
import se.lu.nateko.cp.meta.core.data.LatLonBox
import se.lu.nateko.cp.meta.core.data.Pin
import se.lu.nateko.cp.meta.core.data.Polygon
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.services.sparql.magic.ConcaveHullLengthRatio
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory

object DoiGeoLocationConverter {

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

	def toLatLonBox(circle: Circle) = {
		val metersPerDegree = 111111
		val center = circle.center
		val latRadius = circle.radius / metersPerDegree
		val factor = Math.cos(center.lat.toRadians)

		val minLat = center.lat - latRadius
		val maxLat = center.lat + latRadius
		val minLon = center.lon - latRadius / factor
		val maxLon = center.lon + latRadius / factor

		LatLonBox(
			Position(minLat, minLon, center.alt, None, None),
			Position(maxLat, maxLon, center.alt, None, None),
			circle.label, 
			None
		)
	}

	def toDoiGeoLocationWithPoint(pos: Position): GeoLocation =
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

	// Not yet supported by DataCite
	// def toDoiGeoLocationWithPolygon(polygon: Polygon): GeoLocation =
	// 	GeoLocation(None, None, Some(
	// 		Seq(GeoLocationPolygon(
	// 			polygon.vertices.map(v => GeoLocationPoint(Some(Longitude(v.lon)), Some(Latitude(v.lat))))))
	// 		), polygon.label
	// 	)

	def toDoiGeoLocation(geoCoverage: GeoFeature): Seq[GeoLocation] =
		geoCoverage match {
			case p: Position => Seq(toDoiGeoLocationWithPoint(p))
			case Pin(position, _) => Seq(toDoiGeoLocationWithPoint(position))
			case b: LatLonBox => Seq(toDoiGeoLocationWithBox(b))
			case c: Circle => Seq(toDoiGeoLocationWithBox(toLatLonBox(c)))
			case GeoTrack(points, label, _) => Seq(toDoiGeoLocationWithBox(toLatLonBox(points, label)))
			case Polygon(vertices, label, _) => Seq(toDoiGeoLocationWithBox(toLatLonBox(vertices, label)))
			case fc: FeatureCollection => fc.features.flatMap(toDoiGeoLocation)
		}
	
	def jtsPolygonToDoiBox(polygon: Geometry): GeoLocation =
		val envelope = polygon.getEnvelopeInternal

		GeoLocation(None, Some(
			GeoLocationBox(
				Some(Longitude(envelope.getMinX())), Some(Longitude(envelope.getMaxX())), 
				Some(Latitude(envelope.getMinY())), Some(Latitude(envelope.getMaxY()))
			)), None
		)

	def fromJtsToDoiGeoLocation(geometry: Geometry): GeoLocation =
		geometry match
			case point: JtsPoint => toDoiGeoLocationWithPoint(Position.ofLatLon(point.getY(), point.getX()))
			//TODO Handle polygons as polygons in DataCite, when they support them in their REST API
			case g => jtsPolygonToDoiBox(g)
}
