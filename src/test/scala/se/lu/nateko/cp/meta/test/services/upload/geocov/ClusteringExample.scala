package se.lu.nateko.cp.meta.test.services.upload.geocov
import java.nio.file.{Files, Paths}
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import se.lu.nateko.cp.meta.core.data.{FeatureCollection, GeoFeature, GeoJson}
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovMerger.{mergeIntersecting, representativeCoverage}
import se.lu.nateko.cp.meta.services.upload.geocov.LabeledJtsGeo


object ClusteringExample:
	def convertStringsToJTS(geomStrings: String*): Seq[Geometry] =
		val wktReader = new WKTReader(JtsGeoFactory)
		geomStrings.map(wktReader.read)

	def experimentalExample(): Unit =

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
		val initMerge = mergeIntersecting(labeledTestData.toIndexedSeq)

		initMerge.foreach(g => println(g.geom))

		//val secondMerge = runSecondPass(initMerge)

		// println("epsilon: " + epsilon)
		//secondMerge.foreach(g => println(g.geom))
	end experimentalExample

	@main def collectionExample(): Unit =
		val collFeatures = TestGeoFeatures.readTestInput()
		//Files.writeString(Paths.get("./collGeoJsonAllFeatures.json"), toGeoJson(collFeatures))
		val start = System.nanoTime()
		val clusteredFeatures = representativeCoverage(collFeatures, 100)
		val elapsed = (System.nanoTime() - start) / 1e6
		println(s"Clustered from ${collFeatures.size} to ${clusteredFeatures.size} in $elapsed ms")
		Files.writeString(Paths.get("./collGeoJsonClustered.json"), toGeoJson(clusteredFeatures))


	def toGeoJson(geos: Seq[GeoFeature]): String =
		val coll = FeatureCollection(geos, None, None)
		GeoJson.fromFeatureWithLabels(coll).prettyPrint

end ClusteringExample