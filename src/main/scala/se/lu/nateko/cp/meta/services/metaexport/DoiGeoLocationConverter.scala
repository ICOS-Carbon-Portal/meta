package se.lu.nateko.cp.meta.services.upload

import org.locationtech.jts.algorithm.hull.ConcaveHull
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point as JtsPoint
import org.locationtech.jts.geom.Polygon as JtsPolygon
import org.locationtech.jts.io.geojson.GeoJsonReader
import se.lu.nateko.cp.doi.meta.*
import se.lu.nateko.cp.doi.meta.GeoLocation
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
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.services.metaexport.DoiGeoLocationCreator.LabeledJtsGeo
import se.lu.nateko.cp.meta.services.sparql.magic.ConcaveHullLengthRatio
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory

object DoiGeoLocationConverter:

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

	// Not yet supported by DataCite
	// def fromPolygon(polygon: Polygon): GeoLocation =
	// 	GeoLocation(None, None, Some(
	// 		Seq(GeoLocationPolygon(
	// 			polygon.vertices.map(v => GeoLocationPoint(Some(Longitude(v.lon)), Some(Latitude(v.lat))))))
	// 		), polygon.label
	// 	)

	def fromGeoFeature(geoCoverage: GeoFeature): Seq[GeoLocation] =
		geoCoverage match
			case p: Position => Seq(fromPosition(p))
			case Pin(position, _) => Seq(fromPosition(position))
			case b: LatLonBox => Seq(fromBox(b))
			case c: Circle => Seq(fromBox(toLatLonBox(c)))
			case GeoTrack(points, label, _) => Seq(fromBox(toLatLonBox(points, label)))
			case Polygon(vertices, label, _) => Seq(fromBox(toLatLonBox(vertices, label)))
			case fc: FeatureCollection => fc.features.flatMap(fromGeoFeature)


	// def jtsPolygonToDoiBox(polygon: LabeledJtsGeo): GeoLocation =
	// 	val envelope = polygon.geom.getEnvelopeInternal

	// 	GeoLocation(None, Some(
	// 		GeoLocationBox(
	// 			Some(Longitude(envelope.getMinX())), Some(Longitude(envelope.getMaxX())), 
	// 			Some(Latitude(envelope.getMinY())), Some(Latitude(envelope.getMaxY()))
	// 		)), mergeLabels(polygon.labels)
	// 	)

	// TODO uri for geoFeatures?
	def fromJtsToGeoFeature(geometry: LabeledJtsGeo): Option[GeoFeature] =
		geometry.geom match
			case point: JtsPoint => Some(Position.ofLatLon(point.getY, point.getX))
			case polygon: JtsPolygon => Some(
				Polygon(
					vertices = polygon.getCoordinates().toIndexedSeq.map(c => Position.ofLatLon(c.getY, c.getX)),
					label = mergeLabels(geometry.labels),
					uri = None
				)
			)
			case ls: LineString => Some(
				GeoTrack(
					points = ls.getCoordinates().toIndexedSeq.map(c => Position.ofLatLon(c.getY, c.getX)),
					label = mergeLabels(geometry.labels),
					uri = None
				)
			)
			case gc: GeometryCollection =>
				var fcSeq: Seq[GeoFeature] = Seq.empty
				for (i <- 0 until gc.getNumGeometries)
					val jtsGeom: Geometry = gc.getGeometryN(i)
					val geoFeature = fromJtsToGeoFeature(LabeledJtsGeo(jtsGeom, Seq.empty))
					geoFeature.foreach(gf =>
						fcSeq = fcSeq :+ gf
					)
				Some(FeatureCollection(fcSeq, label = mergeLabels(geometry.labels), None))
			case other => None // TODO handle this case?


	def mergeLabels(labels: Seq[String]): Option[String] =
		val AtcRegex = "^[A-Z]{3}$".r
		val variableRegex = ".+_\\d+_\\d+_\\d+".r

		// convert e.g. "TA_13_8_11" to "TA_n_n_n"
		def reduceIndices(l: String): String =
			if variableRegex.matches(l) then l.replaceAll("_\\d+", "_n") else l

		def lblOrder(l: String): Int = l match
			case StationId(_) => 0
			case AtcRegex() => 1
			case "TA" => 100
			case _ => 10

		Option(
			labels
				.flatMap(_.split("/")
				.map(l => reduceIndices(l.trim)))
				.distinct
				.sortBy(lblOrder)
				.mkString(", ")
		).filterNot(_.isEmpty)

end DoiGeoLocationConverter
