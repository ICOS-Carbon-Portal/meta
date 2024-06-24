package se.lu.nateko.cp.meta.services.metaexport

import org.locationtech.jts.algorithm.Centroid
import se.lu.nateko.cp.meta.services.metaexport.DoiGeoLocationCreator.LabeledJtsGeo

import scala.util.Random

import JtsGeoFeatureConverter.*

object KMeans:

	def generateClusters(points: Seq[LabeledJtsGeo]): Seq[LabeledJtsGeo] =
		val k = 3 // to be decided
		val maxIterations = 100 // to be decided
		val clusters = kMeans(points, k, maxIterations)

		clusters.map(cluster => LabeledJtsGeo(
			concaveHull(makeCollection(cluster.map(_.geom))),
			cluster.flatMap(_.labels))
		)

	end generateClusters

	def kMeans(points: Seq[LabeledJtsGeo], k: Int, maxIterations: Int): Seq[Seq[LabeledJtsGeo]] =
		val random = new Random()
		var centroids = points.take(k)

		for (_ <- 0 until maxIterations)
			val clusters = points.groupBy(findNearestCentroid(_, centroids))
			centroids = clusters.values.map(computeCentroidWithJts).toSeq

		points.groupBy(findNearestCentroid(_, centroids)).values.toSeq

	def findNearestCentroid(point: LabeledJtsGeo, centroids: Seq[LabeledJtsGeo]): LabeledJtsGeo =
		centroids.minBy(labeledC => point.geom.distance(labeledC.geom))

	def computeCentroidWithJts(points: Seq[LabeledJtsGeo]): LabeledJtsGeo =
		val pointCollection = makeCollection(points.map(_.geom))
		LabeledJtsGeo(pointCollection.getCentroid, Seq.empty)

end KMeans
