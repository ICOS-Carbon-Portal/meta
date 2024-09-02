package se.lu.nateko.cp.meta.services.upload.geocov

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.algorithm.ConvexHull
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier
import scala.collection.mutable

object GeoCluster:
	val MaxGeoDigestSize = 15

	def fuse(geos: Seq[LabeledJtsGeo]): Seq[LabeledJtsGeo] =
		if geos.size <= 1 then geos else
			val coordinates = geos.flatMap(_.geom.getCoordinates())
			val hull = new ConvexHull(coordinates.toArray, JtsGeoFactory).getConvexHull()

			if geos.size <= 3 && isVeryThin(hull) then geos else
				val labels = geos.flatMap(_.labels).distinct
				Seq(LabeledJtsGeo(hull, labels).fuse)

	private def isVeryThin(geo: Geometry): Boolean =
		val diam = geo.getEnvelopeInternal.getDiameter
		geo.getArea < 0.05 * diam * diam

	def reducePointsTo(n: Int, geo: Geometry): Geometry =
		assert(n >= 3, "only reduce to polygons")

		def reduceInner(fromTol: Double, tol: Double, toTol: Double): Geometry =
			val attempt = DouglasPeuckerSimplifier.simplify(geo, tol)
			val gotPoints = attempt.getNumPoints
			if gotPoints == n then attempt
			else if gotPoints < n then reduceInner(fromTol, (fromTol + tol) / 2, tol)
			else reduceInner(tol, (tol + toTol) / 2, toTol)

		if geo.getNumPoints <= n then geo else
			val diam = geo.getEnvelopeInternal.getDiameter
			reduceInner(0, 0.01 * diam, diam)

case class LabeledJtsGeo(geom: Geometry, labels: Seq[String]):
	export geom.getArea

	def mergeIfIntersects(geo: LabeledJtsGeo):  Option[LabeledJtsGeo] =
		if geom.intersects(geo.geom) then
			Some(LabeledJtsGeo(geom.union(geo.geom), mergedLabels(geo)))
		else None

	//def distanceTo(geo: LabeledJtsGeo): Double = geom.distance(geo.geom)
	def isWithinDistance(geo: LabeledJtsGeo, maxDistance: Double): Boolean =
		geom.isWithinDistance(geo.geom, maxDistance)

	private def mergedLabels(geo: LabeledJtsGeo): Seq[String] =
		labels ++ geo.labels.filterNot(labels.contains)

	def fuse: LabeledJtsGeo =
		if geom.getNumPoints > GeoCluster.MaxGeoDigestSize then
			copy(geom = GeoCluster.reducePointsTo(GeoCluster.MaxGeoDigestSize, geom))
		else
			this
end LabeledJtsGeo


class ClusterRegistry:
	private type Cluster = mutable.Set[Int]
	private val clusters = mutable.ArrayBuffer.empty[Cluster]
	private val lookup = mutable.HashMap.empty[Int, Cluster]

	def addIndexPair(i: Int, j: Int): Unit = if i != j then
		lookup.get(i) match
			case Some(clusterI) => lookup.get(j) match
				case Some(clusterJ) =>
					if clusterI ne clusterJ then // cluster merge scenario
						clusterJ.foreach: jidx =>
							clusterI.add(jidx)
							lookup.update(jidx, clusterI)
						clusterJ.clear()
				case None =>
					clusterI.add(j)
					lookup.update(j, clusterI)
			case None => lookup.get(j) match
				case Some(cluster) =>
					cluster.add(i)
					lookup.update(i, cluster)
				case None =>
					val cluster = mutable.Set(i, j)
					clusters += cluster
					lookup.update(i, cluster)
					lookup.update(j, cluster)
	end addIndexPair

	def isClustered(index: Int): Boolean = lookup.contains(index)

	def enumerateClusters: Iterator[collection.Set[Int]] = clusters.iterator.filterNot(_.isEmpty)
