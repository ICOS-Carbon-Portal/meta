package se.lu.nateko.cp.meta.test.services.sparql.index

import com.opencsv.{CSVParserBuilder, CSVReaderBuilder}
import org.locationtech.jts.algorithm.hull.ConcaveHull
import org.locationtech.jts.geom.{Coordinate, Envelope, Geometry, GeometryCollection, Point}
import org.locationtech.jts.io.geojson.GeoJsonReader
import org.roaringbitmap.buffer.MutableRoaringBitmap
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.crypto.Md5Sum
import se.lu.nateko.cp.meta.services.sparql.magic.{DataObjCov, DenseCluster, GeoEvent, GeoIndex, GeoLookup, JtsGeoFactory, SparseCluster}

import java.io.FileReader
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.language.dynamics

class GeoIndexTest extends AnyFunSpec{

	// val testFile = "src/test/resources/geocov_small.txt"
	// val testFile = "src/test/resources/geocov_medium.txt"
	val testFile = "src/test/resources/test_input_2.txt"

	def parseCsvLines(): IndexedSeq[GeoEvent] =
		val csvParser = new CSVParserBuilder().withSeparator(',').withQuoteChar('"').build
		val csvReader = new CSVReaderBuilder(new FileReader(testFile)).withCSVParser(csvParser).build

		val lines = csvReader.asScala.iterator
		val rowMaker = GeoEventParser(lines.next())
		lines.zipWithIndex.flatMap(rowMaker.parseRow).toIndexedSeq


	ignore("Manual testing experiments"):
		val index = GeoIndex()
		val events = parseCsvLines()
		events.foreach(index.putQuickly)
		index.arrangeClusters()
		// index.compositeClusters.foreach(_.printTree(1))

		it("wip experiments"):
			val europe = mkBoundingBox(Coordinate(-15, 33), Coordinate(35, 73))
			val world = mkBoundingBox(Coordinate(-180, -90), Coordinate(180, 90))
			
			// westlimit=179.6; southlimit=21.9; eastlimit=-29.9; northlimit=76.7
			val america = mkBoundingBox(Coordinate(-99, 21), Coordinate(-29, 50))

			// westlimit=18.7104; southlimit=64.0408; eastlimit=19.842; northlimit=64.3088
			val tarfala = mkBoundingBox(Coordinate(18.7104, 64.3088), Coordinate(19.842, 64.0408))
			
			//westlimit=9.23; southlimit=54.32; eastlimit=24.97; northlimit=69.83
			val sverige = mkBoundingBox(Coordinate(9.23, 54.32), Coordinate(24.97, 69.83))

			//westlimit=2.42; southlimit=50.55; eastlimit=7.56; northlimit=53.91
			val nl = mkBoundingBox(Coordinate(2.42, 50.55), Coordinate(7.56, 53.91))

			// westlimit=-2.52; southlimit=42.45; eastlimit=2.62; northlimit=46.37
			val southernFrance = mkBoundingBox(Coordinate(-2.52, 42.45), Coordinate(2.62, 46.37))

			//westlimit=-13.57; southlimit=42.45; eastlimit=33.14; northlimit=60.44
			val midEurope = mkBoundingBox(Coordinate(-13.57, 42.45), Coordinate(33.14, 60.44))

			val returned = index.getFilter(america, None)

			assert(returned.toArray() === Array(13))

		it("test remove methods"):
			val deleteEvents = events.map(e => GeoEvent(e.objIdx, false, e.geometry, e.clusterId))
			deleteEvents.foreach(index.put)

			val america = mkBoundingBox(Coordinate(-99, 21), Coordinate(-29, 50))
			val returned = index.getFilter(america, None)

			// index.compositeClusters.foreach(_.printTree(1))

			assert(returned.toArray() === Array[Int]())

	describe("Clustering"):
		val gf = JtsGeoFactory

		def createDenseCluster(pt: Geometry, nbrOfObjs: Int): DenseCluster =
			val bitmap = new MutableRoaringBitmap

			for (i <- 0 until nbrOfObjs)
				bitmap.add(i)

			DenseCluster(pt, bitmap)

		def createSparseCluster(areas: List[Geometry], nbrOfObjs: Int): SparseCluster =
			val indices = 0 until nbrOfObjs
			val bitmap = new MutableRoaringBitmap
			for (i <- indices) bitmap.add(i)

			val joined = GeometryCollection(areas.toArray, JtsGeoFactory)

			SparseCluster(ConcaveHull(joined).getHull(), indices.map(i => DataObjCov(i, areas(i))), bitmap)

		val globalBbox = mkBoundingBox(Coordinate(-180, -60), Coordinate(180, 90))
		val samplePts = List(new Coordinate(0, 0), new Coordinate(0, 1), new Coordinate(1, 0)).map(gf.createPoint)

		it("add objects with same coverage to DenseCluster"):
			val pt = samplePts(0)
			val dc = DenseCluster(pt, new MutableRoaringBitmap)

			dc.addObject(DataObjCov(0, pt))

			val res = dc.getFilter(globalBbox, None)

			assert(!res.isEmpty)
			assert(res.getCardinality == 1)

			dc.addObject(DataObjCov(1, pt))

			val res2 = dc.getFilter(globalBbox, None)

			assert(res2.getCardinality == 2)

		it("SparseCluster with new area is created on addition to DenseCluster"):
			val List(pt1, pt2, pt3) = samplePts

			val initialCluster = createDenseCluster(pt1, 1)
			val twoPointCluster = initialCluster.addObject(DataObjCov(1, pt2))

			val filter = twoPointCluster.getFilter(globalBbox, None)

			assert(twoPointCluster.isInstanceOf[SparseCluster])
			assert(filter.getCardinality == 2)

			val threePointCluster = twoPointCluster.addObject(DataObjCov(2, pt3))

			assert(twoPointCluster.area != threePointCluster.area)

		it("remove geometry from dense cluster with two objects"):
			val pt = samplePts(0)
			val dc = createDenseCluster(pt, 2)

			val res = dc.removeObject(DataObjCov(0, pt))
			val filter = dc.getFilter(globalBbox, None)

			assert(res.isDefined)
			assert(filter.getCardinality == 1)

		it("remove geometry from dense cluster with one object"):
			val pt = samplePts(0)
			val dc = createDenseCluster(pt, 1)

			val res = dc.removeObject(DataObjCov(0, pt))
			val filter = dc.getFilter(globalBbox, None)

			assert(!res.isDefined)
			assert(filter.getCardinality == 0)

		it("remove from empty cluster does nothing"):
			val pt = samplePts(0)
			val dc = createDenseCluster(pt, 0)

			val res = dc.removeObject(DataObjCov(0, pt))

			assert(!res.isDefined)

		it("remove geometry from sparse cluster"):
			val threePointCluster = createSparseCluster(samplePts, 3)

			val twoPointClusterOpt = threePointCluster.removeObject(DataObjCov(0, samplePts(0)))

			assert(twoPointClusterOpt.isDefined)

			twoPointClusterOpt.map: twoPointCluster =>
				val res = twoPointCluster.getFilter(globalBbox, None)
				assert(res.getCardinality == 2)

				val onePointClusterOpt = twoPointCluster.removeObject(DataObjCov(1, globalBbox))

				onePointClusterOpt.map: onePointCluster =>
					val res = onePointCluster.getFilter(globalBbox, None)

					assert(onePointCluster.isInstanceOf[DenseCluster])
					assert(onePointCluster.area != twoPointCluster.area)
					assert(res.getCardinality == 1)

					val empty = onePointCluster.removeObject(DataObjCov(2, globalBbox))

					assert(!empty.isDefined)

	describe("R-tree"):
		def initIndex = GeoIndex()
		val gf = JtsGeoFactory

		val samplePt = gf.createPoint(new Coordinate(0, 0))
		val globalBbox = mkBoundingBox(Coordinate(-180, -60), Coordinate(180, 90))

		it("put event in index with putQuickly and arrangeClusters"):
			val index = initIndex
			val event = GeoEvent(0, true, samplePt, GeoLookup.getClusterId(samplePt))

			index.putQuickly(event)
			index.arrangeClusters()

			val res = index.getFilter(globalBbox, None)

			assert(res.getCardinality() == 1)
			assert(index.allClusters.values.size == 1)

		it("put event in index with put"):
			val index = initIndex
			val event = GeoEvent(0, true, samplePt, GeoLookup.getClusterId(samplePt))

			index.put(event)

			val res = index.getFilter(globalBbox, None)

			assert(res.getCardinality() == 1)
			assert(index.allClusters.values.size == 1)
		
		it("remove event from empty index"):
			val index = initIndex
			val event = GeoEvent(0, false, samplePt, GeoLookup.getClusterId(samplePt))

			index.put(event)

			val res = index.getFilter(globalBbox, None)

			assert(res.getCardinality() == 0)
			assert(index.allClusters.values.size == 0)

		it("remove event from index with one cluster"):
			val index = initIndex
			val event = GeoEvent(0, true, samplePt, GeoLookup.getClusterId(samplePt))

			index.put(event)
			index.put(GeoEvent(event.objIdx, false, event.geometry, event.clusterId))

			val res = index.getFilter(globalBbox, None)

			assert(res.getCardinality() == 0)
			assert(index.allClusters.values.size == 0)

		it("global data obj is placed on top level"):
			val index = initIndex
			val globalCov = gf.toGeometry(new Envelope(-180, 180, -90, 90))
			val event = GeoEvent(0, true, globalCov, GeoLookup.getClusterId(globalCov))

			index.put(event)

			assert(index.rootCluster.children.exists(c =>
				c.isInstanceOf[DenseCluster] && c.area == globalCov
			))
}

class GeoRow(header: Map[String, Int], row: Array[String]) extends Dynamic:
	private def cell(name: String): String = row(header(name))

	def selectDynamic(name: String): String = cell(name)

class GeoEventParser(headerLine: Array[String]):
	private val header: Map[String, Int] = headerLine.zipWithIndex.toMap
	private val f = JtsGeoFactory
	private val reader = GeoJsonReader()

	def hasOwnMinMax(lonMax: String, lonMin: String, latMax: String, latMin: String): Boolean =
		Seq(lonMax, lonMin, latMax, latMin).forall(_.length > 0)

	def parseRow(line: Array[String], idx: Int): Seq[GeoEvent] =
		val row = GeoRow(header, line)

		val stationOpt = if row.station.length > 0 then Some(row.station) else None

		def getStationOrGeometry(geometry: Geometry) = 
			stationOpt.fold(GeoLookup.getClusterId(geometry))(Md5Sum.ofStringBytes(_).toString)

		if row.ownGeoJson.length > 0 then
			val ownGeo = reader.read(row.ownGeoJson)

			ownGeo match
				case coll: GeometryCollection =>
					(0 until coll.getNumGeometries).map: gIdx =>
						val pt = coll.getGeometryN(gIdx)
						GeoEvent(idx, true, pt, GeoLookup.getClusterId(pt))
				case _ =>
					Seq(GeoEvent(idx, true, ownGeo, GeoLookup.getClusterId(ownGeo)))

		else if row.samplingLon.length > 0 && row.samplingLat.length > 0 then
			val coordinate = new Coordinate(row.samplingLon.toDouble, row.samplingLat.toDouble)
			val samplingPoint = f.createPoint(coordinate)
			Seq(GeoEvent(idx, true, samplingPoint, GeoLookup.getClusterId(samplingPoint)))

		else if row.siteGeoJson.length > 0 then
			val siteGeo = reader.read(row.siteGeoJson)
			Seq(GeoEvent(idx, true, siteGeo, getStationOrGeometry(siteGeo)))

		else if hasOwnMinMax(row.lonMax, row.lonMin, row.latMax, row.latMin) then
			val bbox = getBoundingBox(row.lonMax, row.lonMin, row.latMax, row.latMin)
			Seq(GeoEvent(idx, true, bbox, GeoLookup.getClusterId(bbox)))

		else if stationOpt.isDefined && row.stationLon.length > 0 && row.stationLat.length > 0 then
			val coordinate = new Coordinate(row.stationLon.toDouble, row.stationLat.toDouble)
			val stationPoint = f.createPoint(coordinate)
			Seq(GeoEvent(idx, true, stationPoint, GeoLookup.getClusterId(stationPoint)))
		else Seq.empty
	end parseRow

	def getBoundingBox(lonMax: String, lonMin: String, latMax: String, latMin: String): Geometry =
		val lowerLeftPoint = new Coordinate(lonMin.toDouble, latMin.toDouble)
		val upperRightPoint = new Coordinate(lonMax.toDouble, latMax.toDouble)
		mkBoundingBox(lowerLeftPoint, upperRightPoint)

def mkBoundingBox(lowerLeftPoint: Coordinate, upperRightPoint: Coordinate): Geometry =
	JtsGeoFactory.toGeometry(new Envelope(lowerLeftPoint, upperRightPoint))
