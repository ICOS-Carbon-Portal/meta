package se.lu.nateko.cp.meta.test.utils.rdf4j

import java.net.URI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.utils.rdf4j.*

class EnrichedUriTests extends AnyFunSuite {

	given ValueFactory = new MemValueFactory

	test("Java -> RDF4J -> Java round trip with non-ASCII characters"){
		val javaUri = new URI("http://meta.icos-cp.eu/resources/people/Lenka_Folt%C3%BDnov%C3%A1")

		assert(javaUri.toRdf.toJava === javaUri)
	}
}
