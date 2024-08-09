package se.lu.nateko.cp.meta.test.services.upload.geocov

import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovMerger.LabeledJtsGeo
import scala.collection.mutable.ArrayBuffer
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.geom.Geometry
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Point
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovMerger.concaveHull
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovMerger.makeCollection
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovClustering.getMinGeometryDistance
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovClustering.mergeSimpleGeoms
import se.lu.nateko.cp.meta.core.data.PositionUtil.average
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovClustering.runSecondPass


object ClusteringExample:
	def convertStringsToJTS(geomStrings: String*): Seq[Geometry] =
		val wktReader = new WKTReader(JtsGeoFactory)
		geomStrings.map(wktReader.read)

	@main def runClusteringExample() =

		val testData = convertStringsToJTS(
			"POINT (13.1574379 55.2027581)",
			"POINT (13.2453286 55.20746)",
			"POINT (15.2311074 54.4464681)",
			"POINT (15.4837929 54.5166775)",
			"POINT (15.4261147 54.4320923)",
			"POINT (15.7584511 55.8525578)",
			"POINT (13.4485756 55.0928887)",
			"POINT (13.2553187 55.1470764)",
			"POINT (13.3926478 55.1831608)",
			"POINT (15.2108851 54.5541263)",
			"POINT (15.323495 54.5270391)",
			"POLYGON ((13.3227768 55.1666896, 13.295311 55.0630128, 13.6468735 55.0252454, 13.6853257 55.1698271, 13.3227768 55.1666896))",
			"POINT (13.5845314 55.1109594)",
			"POINT (16.5412972 55.2826555)"
		)

		val polygon = convertStringsToJTS("POLYGON ((13.3227768 55.1666896, 13.295311 55.0630128, 13.6468735 55.0252454, 13.6853257 55.1698271, 13.3227768 55.1666896))")(0)
		val polygonCentroid = polygon.getCentroid()

		// println("the polygon centroid: " + polygonCentroid)

		val labeledTestData = testData.map(LabeledJtsGeo(_, Seq.empty))
		val initMerge = mergeSimpleGeoms(labeledTestData, None)

		initMerge.foreach(g => println(g.geom))

		val secondMerge = runSecondPass(initMerge)

		// println("epsilon: " + epsilon)
		secondMerge.foreach(g => println(g.geom))
