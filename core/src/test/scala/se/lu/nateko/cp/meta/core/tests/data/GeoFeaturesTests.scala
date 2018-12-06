package se.lu.nateko.cp.meta.core.tests.data

import org.scalatest.FunSuite
import se.lu.nateko.cp.meta.core.data.Position

class GeoFeaturesTests extends FunSuite {

	test("Position's text representation is as expected"){
		val p = new Position(50.01, 130.123456, None)
		assert(p.lat6 === "50.01")
		assert(p.lon6 === "130.123456")

		val p2 = new Position(-5.0100001, 30.0, None)
		assert(p2.lat6 === "-5.01")
		assert(p2.lon6 === "30")

	}
}
