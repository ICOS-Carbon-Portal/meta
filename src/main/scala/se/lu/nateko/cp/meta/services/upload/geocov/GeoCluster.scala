package se.lu.nateko.cp.meta.services.upload.geocov

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.algorithm.ConvexHull
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory
import org.locationtech.jts.simplify.TopologyPreservingSimplifier

trait GeoCluster:
	def mergeIfContains(geo: LabeledJtsGeo): Option[GeoCluster]
	def mergeIfIntersects(geo: LabeledJtsGeo): Option[GeoCluster]
	def merge(geo: LabeledJtsGeo): GeoCluster
	def distanceTo(geo: LabeledJtsGeo): Double
	def fuse: Seq[LabeledJtsGeo]

case class LabeledJtsGeo(geom: Geometry, labels: Seq[String]) extends GeoCluster:
	export geom.getArea

	def mergeIfContains(geo: LabeledJtsGeo):  Option[GeoCluster] =
		if geom.contains(geo.geom) then
			Some(this.copy(labels = mergedLabels(geo)))
		else None

	def mergeIfIntersects(geo: LabeledJtsGeo):  Option[GeoCluster] =
		if geom.intersects(geo.geom) then
			Some(LabeledJtsGeo(geom.union(geo.geom), mergedLabels(geo)))
		else None

	def merge(geo: LabeledJtsGeo): GeoCluster = ProperCluster(IndexedSeq(this, geo))

	def distanceTo(geo: LabeledJtsGeo): Double = geom.distance(geo.geom)

	private def mergedLabels(geo: LabeledJtsGeo): Seq[String] =
		labels ++ geo.labels.filterNot(labels.contains)

	def fuse: Seq[LabeledJtsGeo] =
		if geom.getNumPoints > 15 then
			val env = geom.getEnvelopeInternal
			val charactSize = Math.sqrt(env.getHeight * env.getHeight + env.getWidth * env.getWidth)
			val simple = TopologyPreservingSimplifier.simplify(geom, charactSize / 50)
			val res = if simple.getNumPoints > 0 then simple else geom.getCentroid
			Seq(LabeledJtsGeo(res, labels))
		else
			Seq(this)

class ProperCluster(geos: IndexedSeq[LabeledJtsGeo]) extends GeoCluster:
	private val coordinates = geos.flatMap(_.geom.getCoordinates())
	private val hull = new ConvexHull(coordinates.toArray, JtsGeoFactory).getConvexHull()

	def mergeIfContains(geo: LabeledJtsGeo):  Option[GeoCluster] =
		if hull.contains(geo.geom) then Some(addGeo(geo)) else None

	def mergeIfIntersects(geo: LabeledJtsGeo):  Option[GeoCluster] =
		if hull.intersects(geo.geom) then Some(addGeo(geo)) else None

	def merge(geo: LabeledJtsGeo): GeoCluster = addGeo(geo)

	def distanceTo(geo: LabeledJtsGeo): Double = hull.distance(geo.geom)

	private def addGeo(geo: LabeledJtsGeo) = ProperCluster(geos :+ geo)

	def fuse: Seq[LabeledJtsGeo] =
		if coordinates.size <= 2 then geos // meaningless to make a hull, keeping two points
		else
			val labels = geos.flatMap(_.labels).distinct
			LabeledJtsGeo(hull, labels).fuse
