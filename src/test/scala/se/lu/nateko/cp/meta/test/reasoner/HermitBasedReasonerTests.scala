package se.lu.nateko.cp.meta.test.reasoner

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.onto.reasoner.HermitBasedReasoner
import se.lu.nateko.cp.meta.test.TestConfig
import se.lu.nateko.cp.meta.utils.owlapi.*

import java.net.URI

class HermitBasedReasonerTests extends AnyFunSpec{

	val owlOnto = TestConfig.owlOnto
	val reasoner = new HermitBasedReasoner(owlOnto)

	describe("getPropertiesWhoseDomainIncludes(owlClass)"){

		it("should return expected props"){
			val owlClass = TestConfig.getOWLClass("Organization")
			val props = reasoner.getPropertiesWhoseDomainIncludes(owlClass)
				.map(oc => getLastFragment(oc.getIRI))
			assert(props.toSet === Set(
					"hasName", "locatedAt", "hasTcId", "hasEtcId", "hasAtcId", "hasOtcId", "hasEmail", "hasDepiction", "hasWebpageElements",
					"hasParisId", "hasMunichId", "hasZurichId"
				)
			)
		}
	}

	describe("getSubClasses"){
		it("should return fully-defined classes that have Equivalent-To block"){
			val spatCovClass = TestConfig.getOWLClass("SpatialCoverage")
			val latLonClass = new URI(TestConfig.ontUri + "LatLonBox")
			val subClasses = reasoner.getSubClasses(spatCovClass, false)
			assert(subClasses.map(_.getIRI.toURI).contains(latLonClass))
		}
	}
}