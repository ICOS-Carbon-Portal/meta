package se.lu.nateko.cp.meta.core.data

import org.scalatest.funsuite.AnyFunSuite

class GeoFeaturesTests extends AnyFunSuite {

	test("Position's text representation is as expected"){
		val p = new Position(50.01, 130.123456, None)
		assert(p.lat6 === "50.01")
		assert(p.lon6 === "130.123456")

		val p2 = new Position(-5.0100001, 30.0, None)
		assert(p2.lat6 === "-5.01")
		assert(p2.lon6 === "30")

	}

	def roundTripTest[T <: GeoFeature](descr: String)(f: T) =
		test(s"$descr GeoJSON (de-)/serialization round trip"){
			assert(GeoFeature.fromGeoJson(f.geo).get === f)
		}

	roundTripTest("Position"){Position(-89.999999, 70.123456, Some(156.45f))}

	roundTripTest("Polygon"){
		Polygon(Seq(
			Position(0, 0, None), Position(13.45, 0, None), Position(13.45, 70.57438, None), Position(0, 70.57438, None)
		))
	}

	roundTripTest("Geotrack"){
		GeoTrack(Seq(
			Position(-3.456, 0, None), Position(13.45, 0, None), Position(13.45, 70.57438, None), Position(0, 70.57438, None)
		))
	}
}
