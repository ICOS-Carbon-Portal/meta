package se.lu.nateko.cp.meta.test

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.Vocab


class OwlOntologyTest extends FunSpec{

	val onto = TestConfig.owlOnto

	describe("OWLOntology.isDeclared"){

		it("should distinguish between entity types even if they have same URI"){
			val realProp = Vocab.getDataProperty("hasName")
			val fakeProp = Vocab.getObjectProperty("hasName")

			assert(onto.isDeclared(realProp))
			assert(!onto.isDeclared(fakeProp))
		}
	}
}