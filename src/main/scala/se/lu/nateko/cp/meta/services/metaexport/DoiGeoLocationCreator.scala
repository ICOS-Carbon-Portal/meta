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

object DoiGeoLocationCreator:

	import JtsGeoFeatureConverter.*

	def representativeCoverage(geoFeatures: Seq[GeoFeature], maxFreePoints: Int): Seq[GeoLocation] =
		val merged = mergeSimpleGeoms(geoFeatures.flatMap(toSimpleGeometries))

		val pointTest: PartialFunction[Geometry, JtsPoint] =
			case p: JtsPoint => p

		val points = merged.collect(pointTest)

		val resGeoms =
			if points.size <= maxFreePoints then
				merged
			else
				val otherGeometries = merged.filterNot(pointTest.isDefinedAt)
				val pointsHull = concaveHull(makeCollection(points))
				otherGeometries :+ pointsHull

		resGeoms.map(DoiGeoLocationConverter.fromJtsToDoiGeoLocation)
	end representativeCoverage


	def mergeSimpleGeoms(gs: Seq[Geometry]): Seq[Geometry] =

		val sortedGeoms = gs.map(hull => (hull, -hull.getArea)).sortBy(_._2).map(_._1)
		var res: ArrayBuffer[Geometry] = ArrayBuffer.empty

		for geom <- sortedGeoms do
			var i = 0
			var added = false
			while (i < res.length && !added)
				if res(i).contains(geom) then
					added = true
				else if geom.intersects(res(i)) then
					res(i) = geom.union(res(i))
					added = true
				i += 1
			if !added then res += geom

		res.toSeq

end DoiGeoLocationCreator

object JtsGeoFeatureConverter:

	def toPoint(p: Position): JtsPoint =
		JtsGeoFactory.createPoint(Coordinate(p.lon, p.lat))

	def makeCollection(geoms: Seq[Geometry]) =
		GeometryCollection(geoms.toArray, JtsGeoFactory)

	def toCollection(points: Seq[Position]) = makeCollection(points.map(toPoint))

	def concaveHull(geom: Geometry) =
		ConcaveHull.concaveHullByLengthRatio(geom, ConcaveHullLengthRatio)

	def toPolygon(polygon: Polygon): JtsPolygon =
		val firstPoint = polygon.vertices.headOption.toArray
		val vertices = (polygon.vertices.toArray ++ firstPoint).map(v => Coordinate(v.lon, v.lat))
		JtsGeoFactory.createPolygon(vertices)

	def toSimpleGeometries(gf: GeoFeature): Seq[Geometry] = gf match
		case b: LatLonBox => Seq(toPolygon(b.asPolygon))
		case c: Circle =>
			val box = DoiGeoLocationConverter.toLatLonBox(c)
			Seq(toPolygon(box.asPolygon))
		case poly: Polygon => Seq(toPolygon(poly))
		case p: Position => Seq(toPoint(p))
		case pin: Pin => Seq(toPoint(pin.position))
		case gt: GeoTrack => Seq(concaveHull(toCollection(gt.points)))
		case fc: FeatureCollection =>
			fc.features.flatMap(toSimpleGeometries)
