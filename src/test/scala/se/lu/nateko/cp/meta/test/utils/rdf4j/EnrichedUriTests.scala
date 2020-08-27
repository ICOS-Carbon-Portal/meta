package se.lu.nateko.cp.meta.test.utils.rdf4j

import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import org.scalatest.funsuite.AnyFunSuite
import java.net.URI
import se.lu.nateko.cp.meta.utils.rdf4j._

class EnrichedUriTests extends AnyFunSuite {

	implicit val factory = new MemValueFactory

	test("Java -> RDF4J -> Java round trip with non-ASCII characters"){
		val javaUri = new URI("http://meta.icos-cp.eu/resources/people/Lenka_Folt%C3%BDnov%C3%A1")

		assert(javaUri.toRdf.toJava === javaUri)
	}
}
