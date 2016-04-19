package se.lu.nateko.cp.meta.test.persistence

import org.scalatest.FunSpec
import org.openrdf.model.impl.ValueFactoryImpl
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.vocabulary.OWL
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import info.aduna.iteration.Iterations
import scala.Iterator

class RdfUpdateLogIngesterTest extends FunSpec{

	describe("RdfUpdate sequence ingestion functionality"){

		import scala.concurrent.ExecutionContext.Implicits.global

		it("works for a simple list of assertions"){
			val f = new ValueFactoryImpl()
			val ctxt = f.createURI("http://www.icos-cp.eu/ontology/")
			val person = f.createURI("http://www.icos-cp.eu/ontology/Person")
			val statement = f.createStatement(person, RDF.TYPE, OWL.CLASS)

			val iter = Iterator(RdfUpdate(statement, true))
			val repo = RdfUpdateLogIngester.ingest(iter, ctxt)
			val conn = repo.getConnection
			val statements = Iterations.asList(conn.getStatements(null, null, null, false, ctxt)).toArray
			conn.close()

			assert(statements.head == statement)
		}
	}
}