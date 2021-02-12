package se.lu.nateko.cp.meta.core.data

import org.scalatest.funsuite.AnyFunSuite
import GeoJson._

class GeoJsonTests extends AnyFunSuite{

	def roundTripTest(descr: String)(f: GeoFeature) =
		test(s"$descr GeoJSON (de-)/serialization round trip"){
			assert(toFeature(fromFeature(f), None).get === f)
		}

	roundTripTest("Position"){Position(-89.999999, 70.123456, Some(156.45f), None)}

	roundTripTest("Polygon"){
		Polygon(Seq(
			Position(0, 0, None, None), Position(13.45, 0, None, None), Position(13.45, 70.57438, None, None), Position(0, 70.57438, None, None)
		), None)
	}

	roundTripTest("Geotrack"){
		GeoTrack(Seq(
			Position(-3.456, 0, None, None), Position(13.45, 0, None, None), Position(13.45, 70.57438, None, None), Position(0, 70.57438, None, None)
		), None)
	}

}
