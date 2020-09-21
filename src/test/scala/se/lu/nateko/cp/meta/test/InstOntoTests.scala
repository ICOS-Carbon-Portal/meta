package se.lu.nateko.cp.meta.test

import org.scalatest.funspec.AnyFunSpec
import java.net.URI
import se.lu.nateko.cp.meta.onto.Onto
import se.lu.nateko.cp.meta.onto.InstOnto

class InstOntoTests extends AnyFunSpec{

	val onto = new Onto(TestConfig.owlOnto)
	val instOnto = new InstOnto(TestConfig.instServer, onto)

	describe("getIndividual"){

		it("correctly constructs display name for Membership individual"){
			val uri = new URI(TestConfig.instOntUri + "atcDirector")
			val indInfo = instOnto.getIndividual(uri)
			
			assert(indInfo.resource.displayName === "Director at Atmosphere Thematic Centre")
		}
		
	}

	describe("getRangeValues"){

		it("lists LatLonBox instances as range values of hasSpatialCoverage (on Station class instances)"){
			val stationClass = new URI(TestConfig.ontUri + "Station")
			val spatialCovProp = new URI(TestConfig.ontUri + "hasSpatialCoverage")
			val globalBox = new URI(TestConfig.instOntUri + "globalLatLonBox")
			val range = instOnto.getRangeValues(stationClass, spatialCovProp)
			assert(range.map(_.uri).contains(globalBox))
		}
	}
}
