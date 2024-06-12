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
	case class LabeledGeometry(geom: Geometry, label: Option[String])

	def filterStationNames(initLabels: Option[String]): Option[String] =
		initLabels.map(labels =>
			val validStationNames: List[String] = labels.split(", ").toList.collect:
				case StationId(station) => station.id

			if (validStationNames.isEmpty) then labels else validStationNames.mkString(", ")
		)

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
				val allPointsLabels = points.flatMap(_.label)
				val combinedPtsLabel = if (allPointsLabels.isEmpty) None else Some(allPointsLabels.mkString(", "))
				val pointsHull = concaveHull(makeCollection(points.map(_.geom)))
				otherGeometries :+ LabeledGeometry(pointsHull, filterStationNames(combinedPtsLabel))

		resGeoms.map(DoiGeoLocationConverter.fromJtsToDoiGeoLocation)
	end representativeCoverage

	def mergeSimpleGeoms(gs: Seq[LabeledGeometry]): Seq[LabeledGeometry] =

		val sortedGeoms = gs.map(hull => (hull, -hull.geom.getArea)).sortBy(_._2).map(_._1)
		var res: ArrayBuffer[LabeledGeometry] = ArrayBuffer.empty

		for labeledGeom <- sortedGeoms do
			val geom = labeledGeom.geom
			val label = labeledGeom.label.getOrElse("")
			var i = 0
			var added = false
			while (i < res.length && !added)
				val newLabel = res(i).label.map(l => 
					if (!l.contains(label)) then l + ", " + label else l
				).orElse(if label.nonEmpty then Some(label) else None)
					
				if res(i).geom.contains(geom) then
					added = true
					res(i) = LabeledGeometry(res(i).geom, newLabel)
				else if geom.intersects(res(i).geom) then
					res(i) = LabeledGeometry(geom.union(res(i).geom), newLabel)
					added = true
				i += 1
			if !added then res += labeledGeom

		res.toSeq.map(lg => LabeledGeometry(lg.geom, filterStationNames(lg.label)))

end DoiGeoLocationCreator

object JtsGeoFeatureConverter:
	import DoiGeoLocationCreator.*

	def toPoint(p: Position): LabeledGeometry =
		LabeledGeometry(JtsGeoFactory.createPoint(Coordinate(p.lon, p.lat)), p.label)

	def makeCollection(geoms: Seq[Geometry]) =
		GeometryCollection(geoms.toArray, JtsGeoFactory)

	def toCollection(points: Seq[Position]) = makeCollection(points.map(toPoint).map(_.geom))

	def concaveHull(geom: Geometry) =
		ConcaveHull.concaveHullByLengthRatio(geom, ConcaveHullLengthRatio)

	def toPolygon(polygon: Polygon): LabeledGeometry =
		val firstPoint = polygon.vertices.headOption.toArray
		val vertices = (polygon.vertices.toArray ++ firstPoint).map(v => Coordinate(v.lon, v.lat))
		LabeledGeometry(JtsGeoFactory.createPolygon(vertices), polygon.label)

	def toSimpleGeometries(gf: GeoFeature): Seq[LabeledGeometry] = gf match
		case b: LatLonBox => Seq(toPolygon(b.asPolygon))
		case c: Circle =>
			val box = DoiGeoLocationConverter.toLatLonBox(c)
			Seq(toPolygon(box.asPolygon))
		case poly: Polygon => Seq(toPolygon(poly))
		case p: Position => Seq(toPoint(p))
		case pin: Pin => Seq(toPoint(pin.position))
		case gt: GeoTrack => Seq(LabeledGeometry(concaveHull(toCollection(gt.points)), gt.label))
		case fc: FeatureCollection =>
			fc.features.flatMap(toSimpleGeometries)
