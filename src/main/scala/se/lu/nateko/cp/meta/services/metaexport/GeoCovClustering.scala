package se.lu.nateko.cp.meta.services.metaexport

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.Point
import se.lu.nateko.cp.meta.core.data.PositionUtil
import se.lu.nateko.cp.meta.services.metaexport.GeoCovMerger.LabeledJtsGeo
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory

import scala.collection.mutable.ArrayBuffer

// TODO Move to the "upload" package
object GeoCovClustering:

	def getMinGeometryDistance(g1: Geometry, g2: Geometry): Double =
		val coordinates1 = g1.getCoordinates()
		val coordinates2 = g2.getCoordinates()

		val distances =
			for
				coord1 <- coordinates1
				coord2 <- coordinates2
			yield getDistance(coord1, coord2)

		distances.min
	end getMinGeometryDistance

	private def getDistance(p1: Coordinate, p2: Coordinate): Double =
		val p1LatLon = (p1.getY, p1.getX)
		val p2LatLon = (p2.getY, p2.getX)

		PositionUtil.distanceInMeters(p1LatLon, p2LatLon)

	def mergeSimpleGeoms(gs: Seq[LabeledJtsGeo], epsilon: Option[Double]): Seq[LabeledJtsGeo] =

		val sortedGeoms = gs.sortBy(hull => -hull.getArea()) // largest first
		var res: ArrayBuffer[LabeledJtsGeo] = ArrayBuffer.empty

		for labeledGeom <- sortedGeoms do
			var i = 0
			var added = false

			while i < res.length && !added do

				res(i).mergeIfIntersects(labeledGeom, epsilon) match
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

	private def calculateEpsilon(geometries: Seq[Geometry]): Double =
		val centroids: Array[Geometry] = geometries.map(_.getCentroid()).toArray
		val geoColl = GeometryCollection(centroids, JtsGeoFactory)
		val centroid = geoColl.getCentroid()

		val distancesToCentroid = geometries.map:
			case p: Point => getDistance(p.getCoordinate(), centroid.getCoordinate())
			case g => getMinGeometryDistance(centroid, g)

		val averageDistToCentroid = if (distancesToCentroid.nonEmpty) distancesToCentroid.sum.toDouble / distancesToCentroid.size else 0.0
		val geometryDiameter = averageDistToCentroid * 2
		val epsilon = 0.2 * geometryDiameter

		epsilon

	def runSecondPass(firstPass: Seq[LabeledJtsGeo]) =
		val epsilon = calculateEpsilon(firstPass.map(_.geom))
		mergeSimpleGeoms(firstPass, Some(epsilon))

