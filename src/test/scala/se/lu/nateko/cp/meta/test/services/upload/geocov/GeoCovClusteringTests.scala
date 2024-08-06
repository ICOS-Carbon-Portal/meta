package se.lu.nateko.cp.meta.test.services.upload.geocov

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.io.geojson.GeoJsonReader
import org.locationtech.jts.io.geojson.GeoJsonWriter
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovClustering
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovMerger.LabeledJtsGeo
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovMerger.makeCollection

import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.mutable.ArrayBuffer

class GeoCovClusteringTests extends AnyFunSpec:

	def labeledToGeoJson(geos: Seq[LabeledJtsGeo]) =
		val writer = new GeoJsonWriter()
		val geosCollection = makeCollection(geos.map(_.geom))
		val json: String = writer.write(geosCollection)

		json

	describe("GeoCovClustering"):

		val reader = GeoJsonReader()
		def geoJsonToGeometry(jsonStr: String): Geometry = reader.read(jsonStr)

		def extractGeometries(geometryCollection: GeometryCollection): Seq[Geometry] = {
			val numGeometries = geometryCollection.getNumGeometries
			val geometriesBuffer = ArrayBuffer[Geometry]()

			for (i <- 0 until numGeometries) {
				geometriesBuffer += geometryCollection.getGeometryN(i)
			}
			geometriesBuffer.toSeq
		}

		// TODO Test second pass merge correctly
		it("second pass merge"):
			// Example geometries from first pass on collection: https://meta.icos-cp.eu/collections/rOZ8Ehl3i0VZp8nqJQR2PB3y
			val filePath = "src/test/resources/DoiGeoLocationTestData.json"
			val jsonContent: String = new String(Files.readAllBytes(Paths.get(filePath)))
			val exampleGeometryCollection: GeometryCollection = geoJsonToGeometry(jsonContent).asInstanceOf[GeometryCollection]
			val geometries: Seq[LabeledJtsGeo] = extractGeometries(exampleGeometryCollection).map(LabeledJtsGeo(_, Seq.empty))

			// println("geometries before: " + geometries.length)
			val secondPass = GeoCovClustering.runSecondPass(geometries)
			// println("second pass: " + secondPass.length)
			// println("second pass: " + labeledToGeoJson(secondPass))
			assert(true)

end GeoCovClusteringTests