package se.lu.nateko.cp.meta.test

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta._

class OntoTests extends FunSpec{

	val onto = new Onto(TestConfig.owlOnto)

	def getClassInfo(localName: String): ClassDto = {
		val classUri = TestConfig.getOWLClass(localName).getIRI.toURI
		onto.getClassInfo(classUri)
	}
	
	describe("getClassInfo"){
		
		describe("for Station class"){
			val props = getClassInfo("Station").properties
			val expected = 9

			it(s"should find $expected properties"){
				assert(props.size === expected)
			}
		}
		
		describe("for Site class"){
			val classInfo = getClassInfo("Site")
			
			it("should find correct value restrictions for latitude"){
				val latitudeRestrictions = classInfo.properties.collect{
					case p: DataPropertyDto if p.resource.displayName == "Latitude" => p.range.restrictions
				}.flatten
				
				val expectedRestrs = Set(MinRestrictionDto(-90), MaxRestrictionDto(90))
				
				assert(latitudeRestrictions.toSet === expectedRestrs)
			}
		}
	}
}