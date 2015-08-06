package se.lu.nateko.cp.meta.sparqlserver

import org.openrdf.repository.Repository
import se.lu.nateko.cp.meta.utils.sesame._
import java.io.ByteArrayOutputStream
import org.openrdf.query.resultio.sparqljson.SPARQLResultsJSONWriterFactory
import org.openrdf.query.QueryLanguage

trait SparqlServer {

	def executeQuery(query: String): String
}

class SesameSparqlServer(repo: Repository) extends SparqlServer{

	def executeQuery(query: String): String = {
		val conn = repo.getConnection

		try{
			val stream = new ByteArrayOutputStream
			val resultWriter = new SPARQLResultsJSONWriterFactory().getWriter(stream)
			val tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query)
			tupleQuery.evaluate(resultWriter)
			stream.toString("UTF-8")
		} finally{
			conn.close()
		}

	}
	
}