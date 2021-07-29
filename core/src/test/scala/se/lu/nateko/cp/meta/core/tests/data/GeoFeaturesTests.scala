package se.lu.nateko.cp.meta.core.data

import org.scalatest.funsuite.AnyFunSuite
import spray.json._
import se.lu.nateko.cp.meta.core.data.JsonSupport._

class GeoFeaturesTests extends AnyFunSuite {

	test("Position's text representation is as expected"){
		val p = new Position(50.01, 130.123456, None, None)
		assert(p.lat6 === "50.01")
		assert(p.lon6 === "130.123456")

		val p2 = new Position(-5.0100001, 30.0, None, None)
		assert(p2.lat6 === "-5.01")
		assert(p2.lon6 === "30")

	}

	test("JSON serialization of FeatureCollection (as GeoFeature) contains GeoJSON only on the top level"){
		val p1 = Position(0, 0, None, None)
		val p2 = Position(0, 1, None, None)
		val coll: GeoFeature = FeatureCollection(Seq(p1, p2), None)
		val featuresJson = coll.toJson.asJsObject.fields("features").prettyPrint
		assert(!featuresJson.contains("\"geo\":"))
	}
}
