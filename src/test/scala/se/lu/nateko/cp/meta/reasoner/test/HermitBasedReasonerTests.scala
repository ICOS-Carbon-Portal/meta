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

	ignore("getDomain(owlProp)"){
		it("should return expected classes"){
			val prop = Vocab.getObjectProperty("hasDirector")
			val siteClass = Vocab.getOWLClass("Site")
			//val domains = reasoner.getFullDomain(prop)
			//val domainSubs = reasoner.getSubClasses(domain, false).getFlattened.toArray
			//reasoner.getParentProps(prop).map(_.getIRI) foreach println
			assert(owlOnto.isDeclared(prop))
			//println(domainSubs.length)
			//assert(domainSubs.size > 0)
		}
	}
}