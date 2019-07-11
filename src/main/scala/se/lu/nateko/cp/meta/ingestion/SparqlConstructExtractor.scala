package se.lu.nateko.cp.meta.ingestion

import org.apache.commons.io.IOUtils
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository

import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jIterationIterator
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SparqlConstructExtractor(pathToQueryRes: String)(implicit ctxt: ExecutionContext) extends Extractor {

	def getStatements(repo: Repository): Ingestion.Statements = Future{
		val queryText = IOUtils.toString(getClass.getResourceAsStream(pathToQueryRes), "UTF-8")

		val conn = repo.getConnection

		try{
			val query = conn.prepareGraphQuery(QueryLanguage.SPARQL, queryText)
			val res = query.evaluate()
			new Rdf4jIterationIterator(res, () => conn.close())
		}catch{
			case err: Throwable =>
				conn.close()
				throw err
		}
	}

}
