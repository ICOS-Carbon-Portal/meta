package se.lu.nateko.cp.meta.reasoner.test

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.Vocab
import se.lu.nateko.cp.meta.reasoner.HermitBasedReasoner
import se.lu.nateko.cp.meta.test.TestConfig
import se.lu.nateko.cp.meta.Utils

class HermitBasedReasonerTests extends FunSpec{

	val owlOnto = TestConfig.owlOnto
	val reasoner = new HermitBasedReasoner(owlOnto)

	describe("getPropertiesWhoseDomainIncludes(owlClass)"){

		it("should return expected props"){
			val owlClass = Vocab.getOWLClass("ThematicCenter")
			val props = reasoner.getPropertiesWhoseDomainIncludes(owlClass)
				.map(oc => Utils.getLastFragment(oc.getIRI))
			assert(props.toSet === Set("hasName", "hasCountry"))
		}
	}

}