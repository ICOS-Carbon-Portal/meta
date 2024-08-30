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
import se.lu.nateko.cp.meta.core.data.PositionUtil
import scala.collection.mutable.ArrayBuffer



object GeoCovMerger:

	def representativeCoverage(geoFeatures: Seq[GeoFeature], threshNgeoms: Int): Seq[GeoFeature] =
		val merged = mergeSimpleGeoms(geoFeatures.flatMap(toSimpleGeometries), None)
		val resGeoms =
			if merged.size <= threshNgeoms then merged
			else
				val size = characteristicSize(merged.map(_.geom))
				mergeSimpleGeoms(merged, Some(size * 0.01))
				//mergeSimpleGeoms(secondPass, None)
		resGeoms.flatMap(fromJtsToGeoFeature)

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

	private def toPoint(p: Position): LabeledJtsGeo =
		LabeledJtsGeo(JtsGeoFactory.createPoint(Coordinate(p.lon, p.lat)), p.label.toSeq)

	private def toCollection(points: Seq[Position]) =
		GeometryCollection(points.map(toPoint).map(_.geom).toArray, JtsGeoFactory)

	private def concaveHull(geom: Geometry) =
		ConcaveHull.concaveHullByLengthRatio(geom, ConcaveHullLengthRatio)

	private def polygonToJts(polygon: Polygon): LabeledJtsGeo =
		val firstPoint = polygon.vertices.headOption.toArray
		val vertices = (polygon.vertices.toArray ++ firstPoint).map(v => Coordinate(v.lon, v.lat))
		LabeledJtsGeo(JtsGeoFactory.createPolygon(vertices), polygon.label.toSeq)

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
		inline def optLabel = mergeLabels(geometry.labels)
		geometry.geom match
			case point: JtsPoint => Some(
				Position.ofLatLon(point.getY, point.getX).withOptLabel(optLabel)
			)
			case polygon: JtsPolygon => Some(
				Polygon(
					vertices = polygon.getCoordinates().toIndexedSeq.map(c => Position.ofLatLon(c.getY, c.getX)),
					label = optLabel,
					uri = None
				)
			)
			case ls: LineString => Some(
				GeoTrack(
					points = ls.getCoordinates().toIndexedSeq.map(c => Position.ofLatLon(c.getY, c.getX)),
					label = optLabel,
					uri = None
				)
			)
			case gc: GeometryCollection =>
				val fcSeq: Seq[GeoFeature] = (0 until gc.getNumGeometries).flatMap: i =>
					val jtsGeom: Geometry = gc.getGeometryN(i)
					fromJtsToGeoFeature(LabeledJtsGeo(jtsGeom, Seq.empty))
				Some(FeatureCollection(fcSeq, optLabel, None))
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

	// def minDistance(g1: Geometry, g2: Geometry): Double =
	// 	val coordinates2 = g2.getCoordinates()

	// 	val distances =
	// 		for
	// 			coord1 <- g1.getCoordinates()
	// 			coord2 <- coordinates2
	// 		yield getDistance(coord1, coord2)

	// 	distances.min
	// end minDistance

	// private def getDistance(p1: Coordinate, p2: Coordinate): Double =
	// 	val p1LatLon = (p1.getY, p1.getX)
	// 	val p2LatLon = (p2.getY, p2.getX)

	// 	PositionUtil.distanceInMeters(p1LatLon, p2LatLon)


	def mergeSimpleGeoms(gs: Seq[LabeledJtsGeo], maxDistance: Option[Double]): Seq[LabeledJtsGeo] =

		val sortedGeoms = gs.sortBy(hull => -hull.getArea()) // largest first
		val res: ArrayBuffer[GeoCluster] = ArrayBuffer.empty

		def tryMergeOne(
			lgeom: LabeledJtsGeo,
			merger: (GeoCluster, LabeledJtsGeo) => Option[GeoCluster]
		): Boolean =
			var merged = false
			for i <- res.indices if !merged do
				merger(res(i), lgeom) match
					case Some(mergedGeom) =>
						merged = true
						res(i) = mergedGeom
					case None => // no merge, do nothing
			merged

		for lgeom <- sortedGeoms do
			if !tryMergeOne(lgeom, _ mergeIfContains _) && !tryMergeOne(lgeom, _ mergeIfIntersects _) then
				val merged = maxDistance.fold(false): maxDist =>
					res.indices.map{i => i -> res(i).distanceTo(lgeom)}.minByOption(_._2).fold(false):
						(i, minDist) =>
							if minDist < maxDist then
								res(i) = res(i).merge(lgeom)
								true
							else false
				//if could not merge with any, add as new
				if !merged then res += lgeom

		res.iterator.flatMap(_.fuse).toSeq
	end mergeSimpleGeoms

	// Characteristic size of a group of geometries in meters
	def characteristicSize(geometries: Seq[Geometry]): Double =
		val centroids = geometries.map(_.getCentroid)
		val centroid = GeometryCollection(centroids.toArray, JtsGeoFactory).getCentroid

		val dists = centroids.map(_.distance(centroid))

		val averageDistToCentroid = if (dists.nonEmpty) dists.sum.toDouble / dists.size else 0.0
		averageDistToCentroid * 2

end GeoCovMerger
