package se.lu.nateko.cp.meta.test.metaexport

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.doi.meta.GeoLocationBox
import se.lu.nateko.cp.meta.services.metaexport.DoiGeoLocationCreator.*
import se.lu.nateko.cp.meta.services.metaexport.JtsGeoFeatureConverter.toSimpleGeometries
import se.lu.nateko.cp.meta.services.metaexport.KMeans
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory
import se.lu.nateko.cp.meta.services.upload.DoiGeoLocationConverter.mergeLabels

import TestGeoFeatures.*


class DoiGeoLocationCreatorTests extends AnyFunSpec:
	describe("DoiGeoLocationCreator"):
		def convertStringsToJTS(geomStrings: String*): Seq[Geometry] =
			val wktReader = new WKTReader(JtsGeoFactory)
			geomStrings.map(wktReader.read)

		it("calling mergeSimpleGeoms with empty seq does nothing"):
			val hulls = mergeSimpleGeoms(Seq())

			assert(hulls == Seq())

		it("two polygons that overlap get merged correctly"):
			val reader = WKTReader()
			val p1 = reader.read("POLYGON ((0.9624173 62.2648867, 0.6987455 59.5310102, 6.4995267 61.2882154, 2.456558 61.603294, 5.0493314 62.8722139, 0.918472 62.5498498, 0.9624173 62.2648867))")
			val p2 = reader.read("POLYGON ((-0.9711764 60.7988811, -2.5092624 59.0598, 4.0825345 57.8880297, 5.3569486 60.345468, -0.9711764 60.7988811))")

			val expected = LabeledJtsGeo(reader.read("POLYGON ((3.7641313758810773 60.459594095063096, 5.3569486 60.345468, 4.0825345 57.8880297, -2.5092624 59.0598, -0.9711764 60.7988811, 0.8087268766290889 60.671350202244895, 0.9624173 62.2648867, 0.918472 62.5498498, 5.0493314 62.8722139, 2.456558 61.603294, 6.4995267 61.2882154, 3.7641313758810773 60.459594095063096))"), Nil)
			val merged = mergeSimpleGeoms(Seq(LabeledJtsGeo(p1, Nil), LabeledJtsGeo(p2, Nil)))

			assert(merged(0) == expected)

		it("data object with less than maximum allowed free points will remain points"):
			val geoms = TestGeoFeatures.modisWithFewerPoints
			val featureCollection = geoms(0).features

			val merged = representativeCoverage(geoms, 30)

			assert(featureCollection.size == merged.size)

		it("data object with more than maximum allowed free points will cluster the points"):
			val geoms = TestGeoFeatures.modisWithMorePoints
			val merged = representativeCoverage(geoms, 30)

			assert(geoms.toSeq != merged)
			merged(0).geoLocationBox.map(box => assert(box.isInstanceOf[GeoLocationBox]))

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

		it("mergeSimpleGeoms from ecosystem data"):
			val geoms = TestGeoFeatures.geoFeatures.flatMap(toSimpleGeometries)
			val merged = mergeSimpleGeoms(geoms)

			for (geom <- geoms)
				assert(merged.exists(_.geom.covers(geom.geom)))

			assert(merged.length == 1)

		it("simple geometries from ocean data"):
			val geoms = TestGeoFeatures.oceanGeoTracks.flatMap(toSimpleGeometries)

			val expectedGeometries = convertStringsToJTS(
				"POLYGON ((-52.267 63.864, -52.275 63.996, -51.726 64.159, -22.047 64.188, -6.766 62, 11.164 57.669, 11.364 57.49, 12.654 56.036, 10.852 56.056, -1.746 59.746, -43.881 59.562, -52.267 63.864))",
				"POLYGON ((-50.167 61.87, -52.277 63.889, -51.889 64.123, -13.521 64.9, 11.866 56.77, 10.835 56.053, 10.252 56.158, 0.899 58.833, -40.092 59.12, -42.009 58.666, -45.139 59.042, -48.277 60.092, -50.167 61.87))",
				"POLYGON ((-48.399 59.791, -52.265 64.002, -51.722 64.167, -23.225 64.141, -6.766 62, -0.776 60.901, 11.13 57.679, 12.667 56.014, 10.823 56.052, 10.414 56.115, 6.649 57.807, -42.663 59.102, -48.399 59.791)))"
			)

			assert(geoms.map(_.geom) == expectedGeometries)

		it("mergeHulls from ocean data"):
			val geoms = TestGeoFeatures.oceanGeoTracks.flatMap(toSimpleGeometries)
			val merged = mergeSimpleGeoms(geoms)

			for (labeledGeom <- geoms)
				val vertices = labeledGeom.geom.getCoordinates().map(JtsGeoFactory.createPoint)
				assert(merged.exists(h =>
					vertices.forall(h.geom.covers)
				))

			assert(merged.length == 1)

		it("mergeLabels"):
			val polygon = convertStringsToJTS("POLYGON ((0 0, 0 0, 0 0, 0 0))")(0)
			val labeledPolygon = LabeledJtsGeo(polygon, List("SW_IN_3_1_1 / SW_IN_3_1_1 / SW_IN_3_1_1", "P_2_1_1", "CD-Ygb", "CH-Dav"))

			val merged = mergeLabels(labeledPolygon.labels)
			val expected = Some("CD-Ygb, CH-Dav, SW_IN_n_n_n, P_n_n_n")

			assert(merged == expected)

		it("KMeans should limit the number of geometries to the specified maximum and keep the features"):
			val geoFeatures = TestGeoFeatures.modisWithFewerPoints.flatMap(toSimpleGeometries)
			val merged = mergeSimpleGeoms(geoFeatures)
			val maxNgeoms = 30
			val clustered = KMeans.cluster(merged, maxNgeoms, 1e-5)

			assert(clustered.length < maxNgeoms)
			assert(clustered.toSet == geoFeatures.toSet)

		it("KMeans should cluster and reduce the number of geometries to the specified maximum"):
			val geoFeatures = TestGeoFeatures.modisWithFewerPoints.flatMap(toSimpleGeometries)
			val merged = mergeSimpleGeoms(geoFeatures)
			val maxNgeoms = 5
			val clustered = KMeans.cluster(merged, maxNgeoms, 1e-5)

			val expectedGeometries = List(
				LabeledJtsGeo(convertStringsToJTS("POLYGON ((140.6551 -34.4704, 140.5891 -34.0021, 146.291606 -34.988282, 150.7236 -33.6152, 148.1517 -35.6566, 145.1878 -37.4259, 144.0944 -37.4222, 140.6551 -34.4704))")(0), List("AU-Cpr", "AU-Cum", "AU-Lox", "AU-Rig", "AU-Tum", "AU-Wac", "AU-Whr", "AU-Wom", "AU-Ync")), 
				LabeledJtsGeo(convertStringsToJTS("LINESTRING (120.6541 -30.1913, 115.7138 -31.3764)")(0), List("AU-GWW", "AU-Gin")), 
				LabeledJtsGeo(convertStringsToJTS("POLYGON ((133.3502 -17.1507, 133.64 -22.287, 133.249 -22.283, 133.3502 -17.1507))")(0), List("AU-ASM", "AU-Stp", "AU-TTE")), 
				LabeledJtsGeo(convertStringsToJTS("POLYGON ((145.6301 -17.1175, 167.192001 -15.4427, 148.4746 -23.8587, 145.6301 -17.1175))")(0), List("AU-Emr", "AU-Rob", "VU-Coc")), 
				LabeledJtsGeo(convertStringsToJTS("POLYGON ((131.1178 -13.0769, 131.1523 -12.4943, 131.3072 -12.5452, 132.4776 -14.5636, 132.3706 -15.2588, 131.3881 -14.1593, 131.3181 -14.0633, 131.1178 -13.0769))")(0), List("AU-Ade", "AU-DaP", "AU-DaS", "AU-Dry", "AU-Fog", "AU-How", "AU-RDF")))

			assert(clustered.length == maxNgeoms)
			assert(clustered == expectedGeometries)

end DoiGeoLocationCreatorTests
