package se.lu.nateko.cp.meta.test.services.sparql.index

import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.geojson.GeoJsonReader
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.metaflow.icos.EtcMetaSource.Vars.stationLon
import se.lu.nateko.cp.meta.services.sparql.magic.DenseCluster
import se.lu.nateko.cp.meta.services.sparql.magic.GeoEvent
import se.lu.nateko.cp.meta.services.sparql.magic.GeoIndex
import se.lu.nateko.cp.meta.services.sparql.magic.SparseCluster

import java.io.FileReader
import scala.collection.mutable.Buffer
import scala.io.Source
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.language.dynamics

class GeoIndexTest extends AnyFunSpec{

	val testFile = "src/test/resources/geocov_small.txt"

	def parseCsvLines(): IndexedSeq[GeoEvent] =
		val csvParser = new CSVParserBuilder().withSeparator(',').withQuoteChar('"').build
		val csvReader = new CSVReaderBuilder(new FileReader(testFile)).withCSVParser(csvParser).build

		val lines = csvReader.asScala.iterator
		val rowMaker = GeoEventParser(lines.next())
		lines.zipWithIndex.flatMap(rowMaker.parseRow).toIndexedSeq


	describe("Insert objects into index"):
		val index = GeoIndex()
		val events = parseCsvLines()
		events.foreach(index.put)

		it("find matching indices"):
			val europe = LatLonBox(Position.ofLatLon(33, -15), Position.ofLatLon(73, 35), None, None)
			val world = LatLonBox(Position.ofLatLon(-90, -180), Position.ofLatLon(90, 180), None, None)
			
			// westlimit=179.6; southlimit=21.9; eastlimit=-29.9; northlimit=76.7
			val america = LatLonBox(Position.ofLatLon(21, -99), Position.ofLatLon(50, -29), None, None)
			val returned = index.getFilter(america, None)

			assert(returned.toArray() === Array(13))
			//println("-------------------- returned -----------------")
			//println(returned.toString())

	describe("Clustering"):
		val gf = GeometryFactory()

		it("add same geometries to dense cluster"):
			val coordinate = new Coordinate(0, 0)
			val pt = gf.createPoint(coordinate)
			val dc = DenseCluster(pt).addObject(0, pt)

			val filter = dc.getFilter(LatLonBox(Position.ofLatLon(-5, -5), Position.ofLatLon(5, 5), None, None), None)

			assert(!filter.isEmpty)
			assert(filter.getCardinality == 1)

			dc.addObject(1, pt)
			val filterSecond = dc.getFilter(LatLonBox(Position.ofLatLon(-5, -5), Position.ofLatLon(5, 5), None, None), None)

			assert(filterSecond.getCardinality == 2)

		it("creates SparseCluster when DenseCluster receives new geometry"):
			val coordinate1 = new Coordinate(0, 0)
			val coordinate2 = new Coordinate(0, 1)

			val pt1 = gf.createPoint(coordinate1)
			val pt2 = gf.createPoint(coordinate2)
			val dc = DenseCluster(pt1).addObject(0, pt1)
			val dc2 = dc.addObject(1, pt2)

			val filter = dc2.getFilter(LatLonBox(Position.ofLatLon(-5, -5), Position.ofLatLon(5, 5), None, None), None)

			// assert(dc2.getClass == se.lu.nateko.cp.meta.services.sparql.magic.SparseCluster)
			assert(filter.getCardinality == 2)
			// assert(dc2.area == Polygon()) 

}

class GeoRow(header: Map[String, Int], row: Array[String]) extends Dynamic:
	private def cell(name: String): String = row(header(name))

	def selectDynamic(name: String): String = cell(name)

class GeoEventParser(headerLine: Array[String]):
	private val header: Map[String, Int] = headerLine.zipWithIndex.toMap
	private val f = GeometryFactory()
	private val reader = GeoJsonReader()

	def hasOwnMinMax(lonMax: String, lonMin: String, latMax: String, latMin: String): Boolean =
		Seq(lonMax, lonMin, latMax, latMin).forall(_.length > 0)

	def parseRow(line: Array[String], idx: Int): Seq[GeoEvent] =
		val row = GeoRow(header, line)

		val stationOpt = if row.station.length > 0 then Some(row.station) else None

		def getClusterId(geometry: String) = stationOpt.getOrElse(geometry)

		if row.ownGeoJson.length > 0 then
			val ownGeo = reader.read(row.ownGeoJson)

			ownGeo match
				case coll: GeometryCollection =>
					(0 until coll.getNumGeometries).map: gIdx =>
						val pt = coll.getGeometryN(gIdx)
						GeoEvent(idx, true, pt, pt.toString())
				case _ =>
					Seq(GeoEvent(idx, true, ownGeo, ownGeo.toString()))

		else if row.samplingLon.length > 0 && row.samplingLat.length > 0 then
			val coordinate = new Coordinate(row.samplingLon.toDouble, row.samplingLat.toDouble)
			val samplingPoint = f.createPoint(coordinate)
			Seq(GeoEvent(idx, true, samplingPoint, samplingPoint.toString()))

		else if row.siteGeoJson.length > 0 then
			val siteGeo = reader.read(row.siteGeoJson)
			Seq(GeoEvent(idx, true, siteGeo, getClusterId(siteGeo.toString())))

		else if hasOwnMinMax(row.lonMax, row.lonMin, row.latMax, row.latMin) then
			val bbox = getBoundingBox(row.lonMax, row.lonMin, row.latMax, row.latMin)
			Seq(GeoEvent(idx, true, bbox, bbox.toString()))

		else if stationOpt.isDefined && row.stationLon.length > 0 && row.stationLat.length > 0 then
			val coordinate = new Coordinate(row.stationLon.toDouble, row.stationLat.toDouble)
			val stationPoint = f.createPoint(coordinate)
			Seq(GeoEvent(idx, true, stationPoint, stationPoint.toString()))
		else Seq.empty
	end parseRow

	def getBoundingBox(lonMax: String, lonMin: String, latMax: String, latMin: String): Geometry =
		val lowerLeftPoint = new Coordinate(lonMin.toDouble, latMin.toDouble)
		val upperRightPoint = new Coordinate(lonMax.toDouble, latMax.toDouble)

		f.toGeometry(new Envelope(lowerLeftPoint, upperRightPoint))
