package se.lu.nateko.cp.meta.core.data

import org.scalatest.funsuite.AnyFunSuite
import GeoJson.*

class GeoJsonTests extends AnyFunSuite{

	def roundTripTest(descr: String)(f: GeoFeature) =
		test(s"$descr GeoJSON (de-)/serialization round trip"){
			assert(toFeature(fromFeature(f), None).get === f)
		}

	roundTripTest("Position"){Position(-89.999999, 70.123456, Some(156.45f), None, None)}

	roundTripTest("Polygon"){
		Polygon(Seq(
			Position(0, 0, None, None, None), 
			Position(13.45, 0, None, None, None), 
			Position(13.45, 70.57438, None, None, None), 
			Position(0, 70.57438, None, None, None)
		), None, None)
	}

	roundTripTest("Geotrack"){
		GeoTrack(Seq(
			Position(-3.456, 0, None, None, None), 
			Position(13.45, 0, None, None, None), 
			Position(13.45, 70.57438, None, None, None), 
			Position(0, 70.57438, None, None, None)
		), None, None)
	}

	roundTripTest("Circle"){
		Circle(Position(24.12345, -179.99999, None, None, None), 25.04f, Some("blabla"), None)
	}

	roundTripTest("FeatureCollection (with inner labels)"){
		val p1 = Position(13.45, 0, None, Some("point 1"), None)
		val p2 = Position(-13.45, 179.99, None, Some("point 2"), None)
		FeatureCollection(Seq(p1, p2), None, None)
	}
}
