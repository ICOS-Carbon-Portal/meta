package se.lu.nateko.cp.meta.test

import org.scalatest.funspec.AnyFunSpec
import org.semanticweb.owlapi.model.parameters.Imports


class OwlOntologyTest extends AnyFunSpec{

	val onto = TestConfig.owlOnto

	describe("OWLOntology.isDeclared"){

		it("should distinguish between entity types even if they have same URI"){
			val realProp = TestConfig.getDataProperty("hasName")
			val fakeProp = TestConfig.getObjectProperty("hasName")

			assert(onto.isDeclared(realProp, Imports.INCLUDED))
			assert(!onto.isDeclared(fakeProp, Imports.INCLUDED))
		}
	}
}