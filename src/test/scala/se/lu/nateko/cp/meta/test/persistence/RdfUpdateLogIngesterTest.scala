package se.lu.nateko.cp.meta.test.persistence

import org.scalatest.funspec.AnyFunSpec
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.OWL
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import org.eclipse.rdf4j.common.iteration.Iterations
import se.lu.nateko.cp.meta.api.CloseableIterator

class RdfUpdateLogIngesterTest extends AnyFunSpec{

	describe("RdfUpdate sequence ingestion functionality"){

		it("works for a simple list of assertions"){
			val f = SimpleValueFactory.getInstance()
			val ctxt = f.createIRI("http://www.icos-cp.eu/ontology/")
			val person = f.createIRI("http://www.icos-cp.eu/ontology/Person")
			val statement = f.createStatement(person, RDF.TYPE, OWL.CLASS, ctxt)

			val iter = CloseableIterator.Wrap(Iterator(RdfUpdate(statement, true)), () => ())
			val repo = RdfUpdateLogIngester.ingestIntoMemory(iter, ctxt)
			val conn = repo.getConnection
			val statements = Iterations.asList(conn.getStatements(null, null, null, false, ctxt)).toArray
			conn.close()

			assert(statements.head == statement)
		}
	}
}