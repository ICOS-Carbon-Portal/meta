package se.lu.nateko.cp.meta.test

import org.scalatest.FunSpec
import java.net.URI
import se.lu.nateko.cp.meta.onto.Onto
import se.lu.nateko.cp.meta.onto.InstOnto

class InstOntoTests extends FunSpec{

	val onto = new Onto(TestConfig.owlOnto)
	val instOnto = new InstOnto(TestConfig.instServer, onto)

	describe("getIndividual"){
		
		it("correctly constructs display name for Membership individual"){
			val uri = new URI(TestConfig.instOntUri + "atcDirector")
			val indInfo = instOnto.getIndividual(uri)
			
			assert(indInfo.resource.displayName === "Director at Atmosphere thematic center")
		}
		
	}
}