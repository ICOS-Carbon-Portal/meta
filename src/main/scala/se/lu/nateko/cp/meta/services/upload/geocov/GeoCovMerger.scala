package se.lu.nateko.cp.meta.services.upload.geocov

import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.algorithm.hull.ConcaveHull
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import se.lu.nateko.cp.doi.meta.GeoLocation
import se.lu.nateko.cp.meta.core.data.Circle
import se.lu.nateko.cp.meta.core.data.FeatureCollection
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.GeoTrack
import se.lu.nateko.cp.meta.core.data.LatLonBox
import se.lu.nateko.cp.meta.core.data.Pin
import se.lu.nateko.cp.meta.core.data.Polygon
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.services.sparql.magic.ConcaveHullLengthRatio
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory
import se.lu.nateko.cp.meta.services.upload.DoiGeoLocationConverter
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

	def representativeCoverage(geoFeatures: Seq[GeoFeature], maxNgeoms: Int): Seq[LabeledJtsGeo] =
		val merged = mergeSimpleGeoms(geoFeatures.flatMap(toSimpleGeometries), None)
		val resGeoms =
			if merged.size <= maxNgeoms then merged
			else
				val secondPass = runSecondPass(merged)
				val finalMerge = mergeSimpleGeoms(secondPass, None)
				finalMerge
		resGeoms

	def toPoint(p: Position): LabeledJtsGeo =
		LabeledJtsGeo(JtsGeoFactory.createPoint(Coordinate(p.lon, p.lat)), p.label.toSeq)

	def makeCollection(geoms: Seq[Geometry]) =
		GeometryCollection(geoms.toArray, JtsGeoFactory)

	def toCollection(points: Seq[Position]) = makeCollection(points.map(toPoint).map(_.geom))

	def concaveHull(geom: Geometry) =
		ConcaveHull.concaveHullByLengthRatio(geom, ConcaveHullLengthRatio)

	def toPolygon(polygon: Polygon): LabeledJtsGeo =
		val firstPoint = polygon.vertices.headOption.toArray
		val vertices = (polygon.vertices.toArray ++ firstPoint).map(v => Coordinate(v.lon, v.lat))
		LabeledJtsGeo(JtsGeoFactory.createPolygon(vertices), polygon.label.toSeq)

	def toSimpleGeometries(gf: GeoFeature): Seq[LabeledJtsGeo] = gf match
		case b: LatLonBox => Seq(toPolygon(b.asPolygon))
		case c: Circle =>
			val box = DoiGeoLocationConverter.toLatLonBox(c)
			Seq(toPolygon(box.asPolygon))
		case poly: Polygon => Seq(toPolygon(poly))
		case p: Position => Seq(toPoint(p))
		case pin: Pin => Seq(toPoint(pin.position))
		case gt: GeoTrack => Seq(LabeledJtsGeo(concaveHull(toCollection(gt.points)), gt.label.toSeq))
		case fc: FeatureCollection =>
			fc.features.flatMap(toSimpleGeometries)

end GeoCovMerger
