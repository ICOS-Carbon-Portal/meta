package se.lu.nateko.cp.meta.services

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository

import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jIterationIterator

class Rdf4jSparqlRunner(repo: Repository)(implicit ctxt: ExecutionContext) extends SparqlRunner {

	def evaluateGraphQuery(q: SparqlQuery): Future[CloseableIterator[Statement]] = Future{
		val conn = repo.getConnection
		try{
			val query = conn.prepareGraphQuery(QueryLanguage.SPARQL, q.query)
			val qres = query.evaluate()
			Future.successful(new Rdf4jIterationIterator(qres, () => conn.close()))
		} catch{
			case err: Throwable =>
				conn.close()
				Future.failed(err)
		}
	}.flatMap(identity)
}
