package se.lu.nateko.cp.meta.test

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta._

class OntoTests extends FunSpec{

	val onto = new Onto(TestConfig.owlOnto)

	def getClassInfo(localName: String): ClassDto = {
		val classUri = Vocab.getOWLClass(localName).getIRI.toURI
		onto.getClassInfo(classUri)
	}
	
	describe("getClassInfo"){
		
		describe("for Station class"){
			val props = getClassInfo("Station").properties
			val expected = 8

			it(s"should find $expected properties"){
				assert(props.size === expected)
			}
		}
		
		describe("for GeoCoordinate class"){
			val classInfo = getClassInfo("GeoCoordinate")
			
			it("should find correct value restrictions for latitude"){
				val latitudeRestrictions = classInfo.properties.collect{
					case p: DataPropertyDto if p.resource.displayName == "hasLatitude" => p.range.restrictions
				}.flatten
				
				val expectedRestrs = Set(MinRestrictionDto(-90), MaxRestrictionDto(90))
				
				assert(latitudeRestrictions.toSet === expectedRestrs)
			}
		}
	}
}