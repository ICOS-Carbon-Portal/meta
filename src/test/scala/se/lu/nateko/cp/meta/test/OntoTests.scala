package se.lu.nateko.cp.meta.test

import org.scalatest.FunSpec
import org.semanticweb.owlapi.apibinding.OWLManager
import se.lu.nateko.cp.meta.Onto
import se.lu.nateko.cp.meta.Vocab
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory

class OntoTests extends FunSpec{
	val manager = OWLManager.createOWLOntologyManager
	val factory = manager.getOWLDataFactory
	val onto = new Onto("/testmeta.owl", manager)
	val reasoner = new StructuralReasonerFactory().createReasoner(onto.ontology)

	ignore("getPropertiesWhoseDomainIncludes(owlClass)"){

		it("should return expected props"){
			val owlClass = Vocab.getOWLClass("ThematicCenter")
			val props = onto.getPropertiesWhoseDomainIncludes(owlClass)
			assert(props.size > 0)
			println(props.head.getIRI)
		}
	}
	
	describe("getDomain(owlProp)"){
		it("should return expected classes"){
			val prop = Vocab.getDataProperty("hasName")
			val siteClass = Vocab.getOWLClass("Site")
			val domain = onto.getDomain(prop)
			val domainSubs = reasoner.getSubClasses(domain, false).getFlattened.toArray
			assert(onto.ontology.isDeclared(prop))
			//println(domainSubs.length)
			//assert(domainSubs.size > 0)
		}
	}
}