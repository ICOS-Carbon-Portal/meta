package se.lu.nateko.cp.meta.test.services.upload.geocov

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovMerger.*
import se.lu.nateko.cp.meta.services.upload.geocov.LabeledJtsGeo
import se.lu.nateko.cp.meta.test.services.upload.geocov.TestGeometries.*

import ClusteringExample.convertStringsToJTS
import TestGeometries.*

class GeoCovMergerTests extends AnyFunSpec:
	it("calling mergeIntersecting with empty seq does nothing"):
		assert(mergeIntersecting(IndexedSeq.empty) === Seq.empty)

	it("two polygons that overlap get merged correctly"):
		val reader = WKTReader()
		val p1 = reader.read("POLYGON ((0.9624173 62.2648867, 0.6987455 59.5310102, 6.4995267 61.2882154, 2.456558 61.603294, 5.0493314 62.8722139, 0.918472 62.5498498, 0.9624173 62.2648867))")
		val p2 = reader.read("POLYGON ((-0.9711764 60.7988811, -2.5092624 59.0598, 4.0825345 57.8880297, 5.3569486 60.345468, -0.9711764 60.7988811))")

		val expected = LabeledJtsGeo(reader.read("POLYGON ((3.7641313758810773 60.459594095063096, 5.3569486 60.345468, 4.0825345 57.8880297, -2.5092624 59.0598, -0.9711764 60.7988811, 0.8087268766290889 60.671350202244895, 0.9624173 62.2648867, 0.918472 62.5498498, 5.0493314 62.8722139, 2.456558 61.603294, 6.4995267 61.2882154, 3.7641313758810773 60.459594095063096))"), Nil)
		val merged = mergeIntersecting(Vector(LabeledJtsGeo(p1, Nil), LabeledJtsGeo(p2, Nil)))

		assert(merged(0) == expected)

	describe("characteristicSize"):
		it("zero for empty list of geometries"):
			assert(characteristicSize(Seq.empty) === 0.0)
		
		it("zero for same points should"):
			val size = characteristicSize(Seq(jtsPoint, jtsPoint))

			assert(size === 0.0)

		it("non-zero for two different points"):
			val pt1 = JtsGeoFactory.createPoint(new Coordinate(0,0))
			val pt2 = JtsGeoFactory.createPoint(new Coordinate(3,4))
			val size = characteristicSize(Seq(pt1, pt2))

			assert(size > 0.0)


	it("geometry contained in another will be merged"):
		val merged = mergeIntersecting(Vector(labeledPolygon1, labeledPoint))

		assert(merged.size === 1)
		val geo = merged.head
		assert(geo.labels.contains("point"))
		assert(geo.labels.contains("polygon1"))

	it("two intersecting geometries will be merged"):
		val merged = mergeIntersecting(Vector(labeledPolygon1, labeledPolygon2))

		assert(merged.size === 1)
		val geo = merged.head
		assert(geo.labels.contains("polygon1"))
		assert(geo.labels.contains("polygon2"))

	it("two non intersecting geometries within epsilon distance will be merged"):
		val epsilon = 400 // guaranteed too large
		val merged = mergeClose(Vector(labeledPolygon1, labeledPolygon3), epsilon)

		assert(merged.size === 1)
		val geo = merged.head
		assert(geo.labels.contains("polygon1"))
		assert(geo.labels.contains("polygon3"))

	it("two geometries further than epsilon dist from each other will not be merged"):
		val epsilon = 0.001d
		val toMerge = Vector(labeledPolygon1, labeledPolygon3)
		val merged = mergeClose(toMerge, epsilon)

		assert(merged === toMerge)

	it("data object with less than maximum allowed free points will remain points"):
		val geoms = TestGeoFeatures.modisWithFewerPoints
		val featureCollection = geoms(0).features

		val merged = representativeCoverage(geoms, 30)

		assert(featureCollection.size == merged.size)

	it("data object with more than maximum allowed free points will cluster the points"):
		val init = TestGeoFeatures.modisWithMorePoints.features
		val merged = representativeCoverage(init, 30)

		assert(merged.length < init.length)

	it("simple geometries from ecosystem data"):
		val geoms = TestGeoFeatures.geoFeatures.flatMap(toSimpleGeometries)

		val expectedGeometries = convertStringsToJTS(
			"POINT (2.780096 48.476357)",
			"POLYGON ((2.779721884892631 48.47564119977188, 2.779721884892631 48.47609480022812, 2.7804061151073687 48.47609480022812, 2.7804061151073687 48.47564119977188, 2.779721884892631 48.47564119977188))",
			"POLYGON ((2.778997883361892 48.47586819977188, 2.778997883361892 48.47632180022812, 2.779682116638108 48.47632180022812, 2.779682116638108 48.47586819977188, 2.778997883361892 48.47586819977188))",
			"POLYGON ((2.7793398803880227 48.47630919977188, 2.7793398803880227 48.47676280022812, 2.7800241196119777 48.47676280022812, 2.7800241196119777 48.47630919977188, 2.7793398803880227 48.47630919977188))",
			"POLYGON ((2.7802158824852485 48.47599819977188, 2.7802158824852485 48.47645180022812, 2.7809001175147516 48.47645180022812, 2.7809001175147516 48.47599819977188, 2.7802158824852485 48.47599819977188))",
			"POLYGON ((2.785777 48.475908, 2.787856 48.478101, 2.786875 48.479848, 2.786456 48.480506, 2.785678 48.480789, 2.784824 48.480814, 2.783953 48.480794, 2.783584 48.480944, 2.783067 48.480903, 2.782683 48.480689, 2.777622 48.477977, 2.775239 48.476735, 2.779432 48.47323, 2.782723 48.474982, 2.783961 48.473986, 2.785777 48.475908)))"
		)

		assert(geoms.map(_.geom) == expectedGeometries)

	it("mergeIntersecting from ecosystem data"):
		val geoms = TestGeoFeatures.geoFeatures.flatMap(toSimpleGeometries)
		val mergedSeq = mergeIntersecting(geoms)

		assert(mergedSeq.length == 1)
		val merged = mergedSeq.head
		assertApproxCovers(merged, geoms)


	it("simple geometries from ocean data"):
		val geoms = TestGeoFeatures.oceanGeoTracks.flatMap(toSimpleGeometries)

		val expectedGeometries = convertStringsToJTS(
			"POLYGON ((-52.267 63.864, -52.275 63.996, -51.726 64.159, -22.047 64.188, -6.766 62, 11.164 57.669, 11.364 57.49, 12.654 56.036, 10.852 56.056, -1.746 59.746, -43.881 59.562, -52.267 63.864))",
			"POLYGON ((-50.167 61.87, -52.277 63.889, -51.889 64.123, -13.521 64.9, 11.866 56.77, 10.835 56.053, 10.252 56.158, 0.899 58.833, -40.092 59.12, -42.009 58.666, -45.139 59.042, -48.277 60.092, -50.167 61.87))",
			"POLYGON ((-48.399 59.791, -52.265 64.002, -51.722 64.167, -23.225 64.141, -6.766 62, -0.776 60.901, 11.13 57.679, 12.667 56.014, 10.823 56.052, 10.414 56.115, 6.649 57.807, -42.663 59.102, -48.399 59.791)))"
		)

		assert(geoms.map(_.geom) == expectedGeometries)

	it("mergeIntersecting from ocean data"):
		val geoms = TestGeoFeatures.oceanGeoTracks.flatMap(toSimpleGeometries)
		val mergedSeq = mergeIntersecting(geoms)
		assert(mergedSeq.length == 1)
		val merged = mergedSeq.head

		assertApproxCovers(merged, geoms)


	def assertApproxCovers(cover: LabeledJtsGeo, gs: Seq[LabeledJtsGeo]) =
		val areaTolerance = cover.geom.getArea * 0.002 // due to point simplification
		for g <- gs do
			val uncoveredArea = g.geom.difference(cover.geom).getArea
			assert(uncoveredArea < areaTolerance)

	it("second pass merge is not done when threshNGeoms is larger than the number after first pass"):
		val geoFeatures = TestGeoFeatures.readTestInput()
		val coverage = representativeCoverage(geoFeatures, 300)
		assert(coverage.length == 212)

	it("second pass merge is done if number too large after first pass"):
		val geoFeatures = TestGeoFeatures.readTestInput()
		val coverage = representativeCoverage(geoFeatures, 100) // threshNGeoms like in production
		assert(coverage.length === 67)

	describe("mergeLabels"):
		it("reduces numerical variable indices, sorts the labels"):
			val labels = List("SW_IN_3_1_1 / SW_IN_3_1_1 / SW_IN_3_1_1", "P_2_1_1", "CD-Ygb", "CH-Dav")

			val merged = mergeLabels(labels)
			val expected = Some("CD-Ygb, CH-Dav, SW_IN_n_n_n, P_n_n_n")

			assert(merged == expected)
end GeoCovMergerTests
