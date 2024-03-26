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
	def createHulls(geoFeatures: Seq[GeoFeature]): Seq[Geometry] =
		var hulls = List.empty

		val geometryCoordinates = geoFeatures.flatMap: gf =>
			gf match
				case fc: FeatureCollection => 
					fc.features.map(createGeometryCollectionFromGeoFeature)
				case other: GeoFeature => 
					Seq(createGeometryCollectionFromGeoFeature(other))

		geometryCoordinates.map: gc =>
			gc.convexHull()

	def sortHullsByArea(convexHulls: List[Geometry]): List[Geometry] =
		val hullsWithArea = convexHulls.map(hull => (hull, hull.getArea))
		val sortedHulls = hullsWithArea.sortBy(_._2).reverse

		sortedHulls.map(_._1)

	def mergeHulls(hulls: Seq[Geometry]): Seq[Geometry] =

		val sortedHulls = sortHullsByArea(hulls.toList)
		var res: ArrayBuffer[Geometry] = ArrayBuffer.empty

		for (hull <- sortedHulls)
			if res.nonEmpty then
				var i = 0
				var added = false
				while (i < res.length && !added)
					if hull.intersects(res(i)) then
						val joined = GeometryCollection(Array(hull, res(i)), JtsGeoFactory)
						val newHull = joined.convexHull()
						res(i) = newHull
						added = true
					i += 1
				if !added then res += hull
			else
				res += hull
		
		res.toSeq

	private def createGeometryCollectionFromGeoFeature(g: GeoFeature): GeometryCollection =

		def createCollection(points: Seq[Position]) =
			GeometryCollection(points.toArray.map(p => JtsGeoFactory.createPoint(Coordinate(p.lon, p.lat))), JtsGeoFactory)

		g match
			case p: Position => createCollection(Seq(p))
			case pin: Pin => createCollection(Seq(pin.position))
			case b: LatLonBox => createCollection(b.asPolygon.vertices)
			case c: Circle => createCollection(DoiGeoLocationConverter.toLatLonBox(c).asPolygon.vertices)
			case gt: GeoTrack => createCollection(gt.points)
			case poly: Polygon => createCollection(poly.vertices)
			case FeatureCollection(_, _, _) => throw Error("Cannot create collection from collection")

	def representativeCov(dataObjs: Seq[DataObject]): Seq[GeoLocation] =
		var geoFeatures: Seq[Option[GeoFeature]] = dataObjs.map(_.coverage)

		var gfExperiment = (geoFeatures.flatten.map:
			case fc: FeatureCollection => fc.features
			case other => Seq(other)
		).flatten

		val (stationPoints, otherGeometries) = gfExperiment.foldLeft((List.empty[Position], List.empty[GeoFeature])):
			case ((points, others), geometry) => geometry match
				case point: Position => if point.label.isDefined then 
					(point :: points, others) else (points, point :: others)
				case _ => (points, geometry :: others)

		val hulls = createHulls(otherGeometries)
		val mergedHulls = mergeHulls(hulls)

		val doiPoints = stationPoints.distinct.flatMap(DoiGeoLocationConverter.toDoiGeoLocation)
		val doiBoxes = mergedHulls.distinct.flatMap(DoiGeoLocationConverter.fromJtsToDoiGeoLocation)

		doiPoints ++ doiBoxes
