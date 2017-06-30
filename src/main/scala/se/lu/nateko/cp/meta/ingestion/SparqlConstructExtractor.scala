package se.lu.nateko.cp.meta.ingestion

import org.apache.commons.io.IOUtils
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository

import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jIterationIterator

class SparqlConstructExtractor(pathToQueryRes: String) extends Extractor {

	def getStatements(repo: Repository): Iterator[Statement] = {
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
