package se.lu.nateko.cp.meta.core.data

import org.scalatest.funsuite.AnyFunSuite
import spray.json.*
import se.lu.nateko.cp.meta.core.data.JsonSupport.given

class GeoFeaturesTests extends AnyFunSuite {

	test("Position's text representation is as expected"){
		val p = Position.ofLatLon(50.01, 130.123456)
		assert(p.lat6 === "50.01")
		assert(p.lon6 === "130.123456")

		val p2 = Position.ofLatLon(-5.0100001, 30.0)
		assert(p2.lat6 === "-5.01")
		assert(p2.lon6 === "30")

	}

	test("JSON serialization of FeatureCollection (as GeoFeature) contains GeoJSON only on the top level"){
		val p1 = Position.ofLatLon(0, 0)
		val p2 = Position.ofLatLon(0, 1)
		val coll: GeoFeature = FeatureCollection(Seq(p1, p2), None, None)
		val featuresJson = coll.toJson.asJsObject.fields("features").prettyPrint
		assert(!featuresJson.contains("\"geo\":"))
	}
}
