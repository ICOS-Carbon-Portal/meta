package se.lu.nateko.cp.meta.test

import scala.collection.JavaConversions._
import se.lu.nateko.cp.meta.Utils

import org.scalatest.FunSpec
import org.semanticweb.owlapi.apibinding.OWLManager
import se.lu.nateko.cp.meta.Onto
import se.lu.nateko.cp.meta.Vocab
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory
import se.lu.nateko.cp.meta.HermitBasedReasoner
import org.semanticweb.owlapi.model.parameters.Imports

object HermitBasedReasonerTests{
	val manager = OWLManager.createOWLOntologyManager
	val factory = manager.getOWLDataFactory
	val owl = Utils.getOntologyFromJarResourceFile("/../classes/owl/cpmeta.owl", manager)
	val reasoner = new HermitBasedReasoner(owl)
}

class HermitBasedReasonerTests extends FunSpec{

	import HermitBasedReasonerTests._

	describe("getPropertiesWhoseDomainIncludes(owlClass)"){

		it("should return expected props"){
			val owlClass = Vocab.getOWLClass("ThematicCenter")
			val props = reasoner.getPropertiesWhoseDomainIncludes(owlClass)
			assert(props.size === 3)
			//props.map(_.getIRI) foreach println
		}
	}

	ignore("getDomain(owlProp)"){
		it("should return expected classes"){
			val prop = Vocab.getObjectProperty("hasDirector")
			val siteClass = Vocab.getOWLClass("Site")
			//val domains = reasoner.getFullDomain(prop)
			//val domainSubs = reasoner.getSubClasses(domain, false).getFlattened.toArray
			reasoner.getParentProps(prop).map(_.getIRI) foreach println
			assert(owl.isDeclared(prop))
			//println(domainSubs.length)
			//assert(domainSubs.size > 0)
		}
	}
}