package se.lu.nateko.cp.meta.services.upload.geocov

import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.algorithm.hull.ConcaveHull
import org.locationtech.jts.geom.{Coordinate, Envelope, Geometry, GeometryCollection, LineString, Point as JtsPoint, Polygon as JtsPolygon}
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.io.geojson.GeoJsonReader
import se.lu.nateko.cp.doi.meta.GeoLocation
import se.lu.nateko.cp.meta.core.data.{Circle, FeatureCollection, FeatureWithGeoJson, GeoFeature, GeoTrack, LatLonBox, Pin, Polygon, Position, PositionUtil}
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.services.sparql.magic.{ConcaveHullLengthRatio, JtsGeoFactory}

import scala.collection.mutable.ArrayBuffer



object GeoCovMerger:

	def representativeCoverage(geoFeatures: Seq[GeoFeature], threshNgeoms: Int): Seq[GeoFeature] =
		// pre-merging top-level feature collections
		val withMergedColls = geoFeatures.flatMap:
			case FeatureCollection(features, lblOpt, _) =>
				val merged = mergeIntersecting(features.flatMap(toSimpleGeometries).toIndexedSeq)
				lblOpt.fold(merged): collLbl =>
					merged.map: lGeom => // preserve the feature collection label
						lGeom.copy(labels = (collLbl +: lGeom.labels).distinct)
			case other =>
				toSimpleGeometries(other)

		val fused = mergeIntersecting(withMergedColls.toIndexedSeq)
		val charSize = characteristicSize(fused.map(_.geom))
		val resGeoms =
			if fused.size <= threshNgeoms then fused
			else mergeClose(fused.toIndexedSeq, 0.01 * charSize)

		resGeoms
			.map: g =>
				g.geom match
					case p: JtsPolygon if p.getEnvelopeInternal.getDiameter < 0.04 * charSize =>
						// small polygons get an accompanying pin to be visible on the map when zoomed out
						val coll = GeometryCollection(Array(p.getCentroid, p), JtsGeoFactory)
						g.copy(geom = coll)
					case _ => g
			.flatMap(fromJtsToGeoFeature)


	def mergeIntersecting(gs: IndexedSeq[LabeledJtsGeo]): Seq[LabeledJtsGeo] =
		mergeGeos(gs, (g1, g2) => g1.geom.intersects(g2.geom), None, gs => Seq(GeoCluster.join(gs).fuse))

	def mergeClose(gs: IndexedSeq[LabeledJtsGeo], maxDistance: Double): Seq[LabeledJtsGeo] =
		mergeGeos(gs, _.isWithinDistance(_, maxDistance), Some(maxDistance), GeoCluster.fuse)

	private def mergeGeos(
		gs: IndexedSeq[LabeledJtsGeo],
		criterion: (LabeledJtsGeo, LabeledJtsGeo) => Boolean,
		padding: Option[Double],
		fuser: IndexedSeq[LabeledJtsGeo] => Seq[LabeledJtsGeo]
	): Seq[LabeledJtsGeo] =

		val rtree = STRtree()

		def envelope(g: LabeledJtsGeo): Envelope =
			val base = g.geom.getEnvelopeInternal
			padding.foreach(dist => base.expandBy(dist))
			base

		gs.zipWithIndex.foreach((g, i) => rtree.insert(envelope(g), i))

		val clusters = new ClusterRegistry

		for i <- gs.indices do
			val currEnv = gs(i).geom.getEnvelopeInternal
			val jList = rtree.query(currEnv)
			//println(s"Got ${jList.size} for $i")
			jList.forEach: idx =>
				val j = idx.asInstanceOf[Integer]
				if i != j && criterion(gs(i), gs(j)) then clusters.addIndexPair(i, j)

		clusters.enumerateClusters
			.flatMap: idxs =>
				fuser(idxs.toIndexedSeq.map(gs.apply))
			.toSeq
			++ gs.indices.filterNot(clusters.isClustered).map(gs.apply)
	end mergeGeos

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
		case FeatureWithGeoJson(feature, _) => toSimpleGeometries(feature)

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


	// Characteristic size of a group of geometries in the coordinate space (likely non-Euclidean)
	def characteristicSize(geometries: Seq[Geometry]): Double =
		val centroids = geometries.map(_.getCentroid)
		val centroid = GeometryCollection(centroids.toArray, JtsGeoFactory).getCentroid

		val dists = centroids.map(_.distance(centroid))

		val averageDistToCentroid = if (dists.nonEmpty) dists.sum.toDouble / dists.size else 0.0
		averageDistToCentroid * 2

end GeoCovMerger
