package se.lu.nateko.cp.meta.test

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta._
import java.net.URI

class InstOntoTests extends FunSpec{

	val onto = new Onto(TestConfig.owlOnto)
	val instOnto = new InstOnto(TestConfig.instServer, onto)

	describe("getIndividual"){
		
		it("correctly constructs display name for Role individual"){
			val uri = new URI(Vocab.ontoIri.toString + "contentexamples/ATC_director")
			val roleInfo = instOnto.getIndividual(uri)
			
			assert(roleInfo.resource.displayName === "Director at Atmosphere thematic center")
		}
		
	}
}