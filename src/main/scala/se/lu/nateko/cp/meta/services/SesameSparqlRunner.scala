package se.lu.nateko.cp.meta.services

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.openrdf.model.Statement
import org.openrdf.query.QueryLanguage
import org.openrdf.repository.Repository

import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.utils.sesame.SesameIterationIterator

class SesameSparqlRunner(repo: Repository)(implicit ctxt: ExecutionContext) extends SparqlRunner {

	def evaluateGraphQuery(q: SparqlQuery): Future[CloseableIterator[Statement]] = Future{
		val conn = repo.getConnection
		try{
			val query = conn.prepareGraphQuery(QueryLanguage.SPARQL, q.query)
			val qres = query.evaluate()
			Future.successful(new SesameIterationIterator(qres, conn.close))
		} catch{
			case err: Throwable =>
				conn.close()
				Future.failed(err)
		}
	}.flatMap(identity)
}
