package se.lu.nateko.cp.meta.test.services.sparql

import java.io.StringReader
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.utils.rdf4j.*

class SparqlTests extends AnyFunSpec{

	val factory: SimpleValueFactory = SimpleValueFactory.getInstance()

	describe("UNION regression is not present in vanilla RDF4J"){
		val repo = new SailRepository(new MemoryStore)
		val rdfData = """
			|@prefix dc11:  <http://purl.org/dc/elements/1.1/> .
			|_:a  dc11:relation _:b .
			|_:a  dc11:title     "SPARQL" .
			|_:b  dc11:title     "SPARQL (updated)" .""".stripMargin

		repo.transact{conn=>
			val reader = StringReader(rdfData)
			conn.add(reader, RDFFormat.TURTLE)
		}

		it("UNION query returns expected results"){
			val q = """
				|PREFIX dc10:  <http://purl.org/dc/elements/1.0/>
				|PREFIX dc11:  <http://purl.org/dc/elements/1.1/>
				|
				|SELECT ?title
				|WHERE {
				|	{?book1 dc11:relation ?book2}
				|	{{ ?book1 dc11:title  ?title } UNION { ?book2 dc11:title  ?title }}
				|}""".stripMargin
			val res = Rdf4jSparqlRunner(repo).evaluateTupleQuery(SparqlQuery(q)).toIndexedSeq
			val titles = res.map(_.getBinding("title").getValue.stringValue).toSet
			assert(titles === Set("SPARQL", "SPARQL (updated)"))
		}
	}
}
