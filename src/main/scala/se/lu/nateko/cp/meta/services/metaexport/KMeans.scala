package se.lu.nateko.cp.meta.services.metaexport

import org.locationtech.jts.algorithm.Centroid
import org.locationtech.jts.geom.Point
import se.lu.nateko.cp.meta.services.metaexport.DoiGeoLocationCreator.LabeledJtsGeo
import scala.util.control.Breaks.{break, breakable}

import JtsGeoFeatureConverter.*

object KMeans:

	def cluster(geoms: Seq[LabeledJtsGeo], k: Int, epsilon: Double): Seq[LabeledJtsGeo] =

		var centroids: Seq[Point] = geoms.take(k).map(_.geom.getCentroid)
		var clusters = Iterable.empty[Seq[LabeledJtsGeo]]

		breakable:
			clusters = geoms.groupBy(findNearestCentroid(_, centroids)).values
			val newCentroids = clusters.map(computeCentroidWithJts).toSeq
			val change = centroids.zip(newCentroids)
				.map: (oldp, newp) =>
					Math.abs(oldp.getX - newp.getX) + Math.abs(oldp.getY - newp.getY)
				.max
			if change < epsilon then break
			else centroids = newCentroids

		clusters
			.map: cluster =>
				LabeledJtsGeo(
					concaveHull(makeCollection(cluster.map(_.geom))),
					cluster.flatMap(_.labels)
				)
			.toSeq
	end cluster

	private def findNearestCentroid(geo: LabeledJtsGeo, centroids: Seq[Point]): Point =
		centroids.minBy(geo.geom.distance)

	private def computeCentroidWithJts(points: Seq[LabeledJtsGeo]): Point =
		makeCollection(points.map(_.geom)).getCentroid

end KMeans
