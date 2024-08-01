package se.lu.nateko.cp.meta.services.metaexport

import se.lu.nateko.cp.meta.services.metaexport.GeoCovMerger.LabeledJtsGeo
import scala.collection.mutable.ArrayBuffer
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.geom.Geometry
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Point
import se.lu.nateko.cp.meta.services.metaexport.GeoCovMerger.concaveHull
import se.lu.nateko.cp.meta.services.metaexport.GeoCovMerger.makeCollection
import se.lu.nateko.cp.meta.core.data.PositionUtil.average


//TODO Get rid of copy-pasted code duplication, move this file to test code
object ClusteringExample:
	// copied from DoiGeoLocationCreator
	case class LabeledJtsGeo(geom: Geometry, labels: Seq[String]):
		export geom.getArea
		def mergeIfIntersects(other: LabeledJtsGeo): Option[LabeledJtsGeo] =
			inline def mergedLabels = labels ++ other.labels.filterNot(labels.contains)
			if geom.contains(other.geom) then
				Some(this.copy(labels = mergedLabels))
			else if geom.intersects(other.geom) then
				Some(LabeledJtsGeo(geom.union(other.geom), mergedLabels))
			else None

		def mergeIfClose(other: LabeledJtsGeo, epsilon: Double): Option[LabeledJtsGeo] =
			inline def mergedLabels = labels ++ other.labels.filterNot(labels.contains)
			if getMinGeometryDistance(this.geom, other.geom) <= epsilon then
				val hull = concaveHull(makeCollection(Seq(geom, other.geom)))
				Some(LabeledJtsGeo(hull, mergedLabels))
			else None

	def getMinGeometryDistance(g1: Geometry, g2: Geometry): Double =
		val coordinates1 = g1.getCoordinates()
		val coordinates2 = g2.getCoordinates()

		val distances =
			for
				coord1 <- coordinates1
				coord2 <- coordinates2
			yield getDistance(coord1, coord2)

		distances.min

	def getDistance(p1: Coordinate, p2: Coordinate): Double =
		val lat1 = p1.getY
		val lon1 = p1.getX
		val lat2 = p2.getY
		val lon2 = p2.getX

		val R = 6371.0  // Earth radius in kilometers
		val dLat = Math.toRadians(lat2 - lat1)
		val dLon = Math.toRadians(lon2 - lon1)
		val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
		val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
		val distance = R * c

		distance
	end getDistance

	// copied from test
	def convertStringsToJTS(geomStrings: String*): Seq[Geometry] =
		val wktReader = new WKTReader(JtsGeoFactory)
		geomStrings.map(wktReader.read)

	def mergeSimpleGeoms(gs: Seq[LabeledJtsGeo], mergeCriteria: (LabeledJtsGeo, LabeledJtsGeo) => Option[LabeledJtsGeo]): Seq[LabeledJtsGeo] =

		val sortedGeoms = gs.map(hull => (hull, -hull.getArea())).sortBy(_._2).map(_._1)
		var res: ArrayBuffer[LabeledJtsGeo] = ArrayBuffer.empty

		for labeledGeom <- sortedGeoms do
			var i = 0
			var added = false
			while i < res.length && !added do

				mergeCriteria(res(i), labeledGeom) match
					case Some(mergedGeom) =>
						added = true
						res(i) = mergedGeom
					case None =>
						//no overlap, therefore no merge, do nothing

				i += 1
			//if could not merge with any, add as new
			if !added then res += labeledGeom

		res.toSeq
	end mergeSimpleGeoms

	def calculateEpsilon(geometries: Seq[Geometry]): Double =
		val centroids: Array[Geometry] = geometries.map(_.getCentroid()).toArray
		val geoColl = GeometryCollection(centroids, JtsGeoFactory)
		val centroid = geoColl.getCentroid()

		// println("the centroid: " + centroid)

		val distancesToCentroid = geometries.map(g => 
			g match
				case p: Point => getDistance(p.getCoordinate(), centroid.getCoordinate())
				case _ => getMinGeometryDistance(centroid, g)
		)

		val averageDistToCentroid = if (distancesToCentroid.nonEmpty) distancesToCentroid.sum.toDouble / distancesToCentroid.size else 0.0
		val geometryDiameter = averageDistToCentroid * 2

		val epsilon = 0.2 * geometryDiameter

		epsilon

	@main def runClusteringExample() =

		val testData = convertStringsToJTS(
			"POINT (13.1574379 55.2027581)",
			"POINT (13.2453286 55.20746)",
			"POINT (15.2311074 54.4464681)",
			"POINT (15.4837929 54.5166775)",
			"POINT (15.4261147 54.4320923)",
			"POINT (15.7584511 55.8525578)",
			"POINT (13.4485756 55.0928887)",
			"POINT (13.2553187 55.1470764)",
			"POINT (13.3926478 55.1831608)",
			"POINT (15.2108851 54.5541263)",
			"POINT (15.323495 54.5270391)",
			"POLYGON ((13.3227768 55.1666896, 13.295311 55.0630128, 13.6468735 55.0252454, 13.6853257 55.1698271, 13.3227768 55.1666896))",
			"POINT (13.5845314 55.1109594)",
			"POINT (16.5412972 55.2826555)"
		)

		val polygon = convertStringsToJTS("POLYGON ((13.3227768 55.1666896, 13.295311 55.0630128, 13.6468735 55.0252454, 13.6853257 55.1698271, 13.3227768 55.1666896))")(0)
		val polygonCentroid = polygon.getCentroid()

		// println("the polygon centroid: " + polygonCentroid)

		val labeledTestData = testData.map(LabeledJtsGeo(_, Seq.empty))
		val initMerge = mergeSimpleGeoms(labeledTestData, (g1, g2) => g1.mergeIfIntersects(g2))

		initMerge.foreach(g => println(g.geom))

		val epsilon = calculateEpsilon(initMerge.map(_.geom))
		val secondMerge = mergeSimpleGeoms(initMerge, (g1, g2) => g1.mergeIfClose(g2, epsilon))

		// println("epsilon: " + epsilon)
		secondMerge.foreach(g => println(g.geom))
