package se.lu.nateko.cp.meta.test.services.sparql.index

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.sparql.magic.GeoEvent
import se.lu.nateko.cp.meta.services.sparql.magic.GeoIndex
import scala.io.Source
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.FileReader
import collection.convert.ImplicitConversions.*
import scala.collection.mutable.ArrayBuffer
import se.lu.nateko.cp.meta.metaflow.icos.EtcMetaSource.Vars.stationLon
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.io.geojson.GeoJsonReader
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.LinearRing
import se.lu.nateko.cp.meta.services.sparql.magic.DenseCluster
import se.lu.nateko.cp.meta.core.data.*
import org.locationtech.jts.geom.GeometryCollection
import se.lu.nateko.cp.meta.services.sparql.magic.SparseCluster
import org.locationtech.jts.geom.Polygon

class GeoIndexTest extends AnyFunSpec{

	val testFile = "src/test/resources/test_input_2.txt"

	def hasOwnMinMax(lonMax: String, lonMin: String, latMax: String, latMin: String): Boolean =
		Seq(lonMax, lonMin, latMax, latMin).forall(_.length > 0)

	def getBoundingBox(lonMax: String, lonMin: String, latMax: String, latMin: String, f: GeometryFactory): Polygon =
		val lowerLeftPoint = new Coordinate(lonMin.toDouble, latMin.toDouble)
		val upperRightPoint = new Coordinate(lonMax.toDouble, latMax.toDouble)

		val boundingBox = new Envelope(lowerLeftPoint, upperRightPoint)

		val shell = Array(
			new Coordinate(boundingBox.getMinX, boundingBox.getMinY),
			new Coordinate(boundingBox.getMaxX, boundingBox.getMinY),
			new Coordinate(boundingBox.getMaxX, boundingBox.getMaxY),
			new Coordinate(boundingBox.getMinX, boundingBox.getMaxY),
			new Coordinate(boundingBox.getMinX, boundingBox.getMinY)
		)

		val linearRing = f.createLinearRing(shell)
		f.createPolygon(linearRing, null)

	def parseCsvLines() =
		val csvParser = new CSVParserBuilder().withSeparator(',').withQuoteChar('"').build
		val csvReader = new CSVReaderBuilder(new FileReader("src/test/resources/small.txt")).withCSVParser(csvParser).build
		// val csvReader = new CSVReaderBuilder(new FileReader("src/test/resources/test_input_2.txt")).withCSVParser(csvParser).build
		val events = ArrayBuffer[GeoEvent]()

		var idx = 0

		var rowCount = 2
		for (row <- csvReader.readAll().drop(1)) {
			// println("row: " + rowCount)
			row.toVector match {
				case Vector(spec,dobj,acq,station,siteGeoJson,samplingLat,samplingLon,stationLat,stationLon,ownGeoJson,lonMax,lonMin,latMax,latMin) =>
					val f = GeometryFactory()
					val reader = GeoJsonReader()
					val stationOpt = if station.length > 0 then Some(station) else None

					def getClusterId(geometry: String) = stationOpt.getOrElse(geometry)

					if ownGeoJson.length > 0 then
						val ownGeo = reader.read(ownGeoJson)

						ownGeo match
							case coll: GeometryCollection => 
								val nbrOfGeometries = coll.getNumGeometries()
								for (i <- 0 to nbrOfGeometries - 1)
									val pt = coll.getGeometryN(i) // more generic than pt?
									events.append(GeoEvent(idx, true, pt, pt.toString()))
							case _ => events.append(GeoEvent(idx, true, ownGeo, ownGeo.toString()))

					else if samplingLon.length > 0 && samplingLat.length > 0 then
						val coordinate = new Coordinate(samplingLon.toDouble, samplingLat.toDouble)
						val samplingPoint = f.createPoint(coordinate)
						events.append(GeoEvent(idx, true, samplingPoint, samplingPoint.toString()))

					else if siteGeoJson.length > 0 then
						val siteGeo = reader.read(siteGeoJson)
						events.append(GeoEvent(idx, true, siteGeo, getClusterId(siteGeo.toString())))

					else if hasOwnMinMax(lonMax, lonMin, latMax, latMin) then
						val bbox = getBoundingBox(lonMax, lonMin, latMax, latMin, f)
						events.append(GeoEvent(idx, true, bbox, bbox.toString()))

					else if stationOpt.isDefined && stationLon.length > 0 && stationLat.length > 0 then
						val coordinate = new Coordinate(stationLon.toDouble, stationLat.toDouble)
						val stationPoint = f.createPoint(coordinate)
						events.append(GeoEvent(idx, true, stationPoint, stationPoint.toString()))
				case _ =>
					print("")
			}
			rowCount += 1
			idx += 1
		}
		events

	describe("Insert objects into index"):
		val events = parseCsvLines()
		val index = GeoIndex()
		val gf = GeometryFactory()

		it("inserts one object"):
			println("number of events: " + events.size)
			events.foreach(index.put)
			assert(true)

		it("find matching indices"):
			val europe = LatLonBox(Position.ofLatLon(33, -15), Position.ofLatLon(73, 35), None, None)
			val world = LatLonBox(Position.ofLatLon(-90, -180), Position.ofLatLon(90, 180), None, None)
			
			// westlimit=179.6; southlimit=21.9; eastlimit=-29.9; northlimit=76.7
			val america = LatLonBox(Position.ofLatLon(21, -99), Position.ofLatLon(50, -29), None, None)
			val returned = index.getFilter(america, None)

			println("-------------------- returned -----------------")
			println(returned.toString())

		it("add same geometries to dense cluster"):
			val coordinate = new Coordinate(0, 0)
			val pt = gf.createPoint(coordinate)
			val dc = DenseCluster(pt).addObject(0, pt)

			val filter = dc.getFilter(LatLonBox(Position.ofLatLon(-5, -5), Position.ofLatLon(5, 5), None, None), None)

			assert(filter.nonEmpty)
			assert(filter.size == 1)

			dc.addObject(1, pt)
			val filterSecond = dc.getFilter(LatLonBox(Position.ofLatLon(-5, -5), Position.ofLatLon(5, 5), None, None), None)
			
			assert(filterSecond.size == 2)

		it("creates SparseCluster when DenseCluster receives new geometry"):
			val coordinate1 = new Coordinate(0, 0)
			val coordinate2 = new Coordinate(0, 1)

			val pt1 = gf.createPoint(coordinate1)
			val pt2 = gf.createPoint(coordinate2)
			val dc = DenseCluster(pt1).addObject(0, pt1)
			val dc2 = dc.addObject(1, pt2)

			val filter = dc2.getFilter(LatLonBox(Position.ofLatLon(-5, -5), Position.ofLatLon(5, 5), None, None), None)

			// assert(dc2.getClass == se.lu.nateko.cp.meta.services.sparql.magic.SparseCluster)
			assert(filter.size == 2)
			// assert(dc2.area == Polygon()) 

}
