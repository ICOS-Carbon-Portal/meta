package se.lu.nateko.cp.meta.services.upload.geocov

import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.algorithm.hull.ConcaveHull
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point as JtsPoint
import org.locationtech.jts.geom.Polygon as JtsPolygon
import org.locationtech.jts.io.geojson.GeoJsonReader
import se.lu.nateko.cp.doi.meta.GeoLocation
import se.lu.nateko.cp.meta.core.data.Circle
import se.lu.nateko.cp.meta.core.data.FeatureCollection
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.GeoTrack
import se.lu.nateko.cp.meta.core.data.LatLonBox
import se.lu.nateko.cp.meta.core.data.Pin
import se.lu.nateko.cp.meta.core.data.Polygon
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.services.sparql.magic.ConcaveHullLengthRatio
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovClustering.*



object GeoCovMerger:

	case class LabeledJtsGeo(geom: Geometry, labels: Seq[String]):
		export geom.getArea

		def mergeIfIntersects(other: LabeledJtsGeo, epsilon: Option[Double]): Option[LabeledJtsGeo] =
			inline def mergedLabels = labels ++ other.labels.filterNot(labels.contains)
			inline def isCloserThanEpsilon = epsilon.map(getMinGeometryDistance(geom, other.geom) < _).getOrElse(false)

			if geom.contains(other.geom) then
				Some(this.copy(labels = mergedLabels))
			else if geom.intersects(other.geom) then
				Some(LabeledJtsGeo(geom.union(other.geom), mergedLabels))
			else if isCloserThanEpsilon then
				val coordinates = geom.getCoordinates() ++ other.geom.getCoordinates()
				val hull = new ConvexHull(coordinates, JtsGeoFactory).getConvexHull()
				Some(LabeledJtsGeo(hull, mergedLabels))
			else None

	def representativeCoverage(geoFeatures: Seq[GeoFeature], maxNgeoms: Int): Seq[GeoFeature] =
		val merged = mergeSimpleGeoms(geoFeatures.flatMap(toSimpleGeometries), None)
		val resGeoms =
			if merged.size <= maxNgeoms then merged
			else
				val secondPass = runSecondPass(merged)
				val finalMerge = mergeSimpleGeoms(secondPass, None)
				finalMerge
		resGeoms.flatMap(fromJtsToGeoFeature)

	def toPoint(p: Position): LabeledJtsGeo =
		LabeledJtsGeo(JtsGeoFactory.createPoint(Coordinate(p.lon, p.lat)), p.label.toSeq)

	def makeCollection(geoms: Seq[Geometry]) =
		GeometryCollection(geoms.toArray, JtsGeoFactory)

	def toCollection(points: Seq[Position]) = makeCollection(points.map(toPoint).map(_.geom))

	def concaveHull(geom: Geometry) =
		ConcaveHull.concaveHullByLengthRatio(geom, ConcaveHullLengthRatio)

	private def polygonToJts(polygon: Polygon): LabeledJtsGeo =
		val firstPoint = polygon.vertices.headOption.toArray
		val vertices = (polygon.vertices.toArray ++ firstPoint).map(v => Coordinate(v.lon, v.lat))
		LabeledJtsGeo(JtsGeoFactory.createPolygon(vertices), polygon.label.toSeq)

	def toSimpleGeometries(gf: GeoFeature): Seq[LabeledJtsGeo] = gf match
		case b: LatLonBox => Seq(polygonToJts(b.asPolygon))
		case c: Circle =>
			Seq(polygonToJts(circleToBox(c).asPolygon))
		case poly: Polygon => Seq(polygonToJts(poly))
		case p: Position => Seq(toPoint(p))
		case pin: Pin => Seq(toPoint(pin.position))
		case gt: GeoTrack => Seq(LabeledJtsGeo(concaveHull(toCollection(gt.points)), gt.label.toSeq))
		case fc: FeatureCollection =>
			fc.features.flatMap(toSimpleGeometries)

	def circleToBox(circle: Circle): LatLonBox =
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
	end circleToBox

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
				val fcSeq: Seq[GeoFeature] = (0 until gc.getNumGeometries).flatMap: i =>
					val jtsGeom: Geometry = gc.getGeometryN(i)
					fromJtsToGeoFeature(LabeledJtsGeo(jtsGeom, Seq.empty))
				Some(FeatureCollection(fcSeq, label = mergeLabels(geometry.labels), None))
			case other => None // quietly ignoring unsupported JTS types


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

end GeoCovMerger
