package se.lu.nateko.cp.meta.services.metaexport

import com.fasterxml.jackson.annotation.JsonFormat.Feature
import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.algorithm.hull.ConcaveHull
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.Point as JtsPoint
import org.locationtech.jts.geom.Polygon as JtsPolygon
import se.lu.nateko.cp.doi.meta.GeoLocation
import se.lu.nateko.cp.meta.core.data.Circle
import se.lu.nateko.cp.meta.core.data.DataObject
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

import scala.collection.mutable.ArrayBuffer
import se.lu.nateko.cp.meta.core.etcupload.StationId

object DoiGeoLocationCreator:
	
	import JtsGeoFeatureConverter.*
	case class StationLabel(label: String)
	case class LabeledJtsGeo(geom: Geometry, labels: Seq[String]):
		export geom.getArea
		def mergeIfIntersects(other: LabeledJtsGeo): Option[LabeledJtsGeo] =
			inline def mergedLabels = labels ++ other.labels.filterNot(labels.contains)
			if geom.contains(other.geom) then
				Some(this.copy(labels = mergedLabels))
			else if geom.intersects(other.geom) then
				Some(LabeledJtsGeo(geom.union(other.geom), mergedLabels))
			else None

	def representativeCoverage(geoFeatures: Seq[GeoFeature], maxFreePoints: Int): Seq[GeoLocation] =
		val merged = mergeSimpleGeoms(geoFeatures.flatMap(toSimpleGeometries))

		val pointTest: PartialFunction[Geometry, JtsPoint] =
			case p: JtsPoint => p

		val points = merged.filter(g => pointTest.isDefinedAt(g.geom))

		val resGeoms =
			if points.size <= maxFreePoints then
				merged
			else
				val otherGeometries = merged.filterNot(g => pointTest.isDefinedAt(g.geom))
				val pointsHull = concaveHull(makeCollection(points.map(_.geom)))
				otherGeometries :+ LabeledJtsGeo(pointsHull, points.flatMap(_.labels))

		resGeoms.map(DoiGeoLocationConverter.fromJtsToDoiGeoLocation)
	end representativeCoverage

	def mergeSimpleGeoms(gs: Seq[LabeledJtsGeo]): Seq[LabeledJtsGeo] =

		val sortedGeoms = gs.map(hull => (hull, -hull.getArea())).sortBy(_._2).map(_._1)
		var res: ArrayBuffer[LabeledJtsGeo] = ArrayBuffer.empty

		for labeledGeom <- sortedGeoms do
			var i = 0
			var added = false
			while i < res.length && !added do

				res(i).mergeIfIntersects(labeledGeom) match
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

end DoiGeoLocationCreator

object JtsGeoFeatureConverter:
	import DoiGeoLocationCreator.*

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
