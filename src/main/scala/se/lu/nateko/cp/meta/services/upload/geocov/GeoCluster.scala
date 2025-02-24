package se.lu.nateko.cp.meta.services.upload.geocov

import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory

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
		val diam = geo.getEnvelopeInternal.getDiameter

		def reduceInner(fromTol: Double, tol: Double, toTolOpt: Option[Double]): Geometry =
			if tol > diam * 10 then geo // failed to reduce the points despite huge tolerance
			else
				val attempt = DouglasPeuckerSimplifier.simplify(geo, tol)
				val gotPoints = attempt.getNumPoints

				if gotPoints == n || (tol - fromTol) / tol < 0.001 then
					attempt // got exact result or converged
				else if gotPoints < n then
					// too strong reduction, reduce tolerance
					reduceInner(fromTol, (fromTol + tol) / 2, Some(tol))
				else toTolOpt match
					case Some(toTol) =>
						// too weak reduction, go half way to the known upper limit of tolerance
						reduceInner(tol, (tol + toTol) / 2, Some(toTol))
					case None =>
						// too weak reduction, no known upper limit, so double the tolerance
						reduceInner(tol, tol * 2, None)

		if geo.getNumPoints <= n then geo else reduceInner(0, 0.01 * diam, None)
	end reducePointsTo

	def join(geos: Seq[LabeledJtsGeo]): LabeledJtsGeo =
		import scala.jdk.CollectionConverters.SeqHasAsJava
		val jGeom = UnaryUnionOp.union(geos.map(_.geom).asJava)
		LabeledJtsGeo(jGeom, geos.flatMap(_.labels).distinct)

end GeoCluster


final case class LabeledJtsGeo(geom: Geometry, labels: Seq[String]):
	export geom.getArea

	def isWithinDistance(geo: LabeledJtsGeo, maxDistance: Double): Boolean =
		geom.isWithinDistance(geo.geom, maxDistance)

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

end ClusterRegistry
