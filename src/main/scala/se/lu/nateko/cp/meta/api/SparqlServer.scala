package se.lu.nateko.cp.meta.api

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import scala.concurrent.Future
import org.openrdf.model.Statement

case class SparqlQuery(query: String)

trait SparqlServer {
	/**
	 * Executes SPARQL SELECT, CONSTRUCT, DESCRIBE queries
	 * Serializes the query results to one of the standard formats, depending on HTTP content negotiation
	 */
	def marshaller: ToResponseMarshaller[SparqlQuery]
}

trait SparqlRunner{

	def evaluateGraphQuery(q: SparqlQuery): Future[CloseableIterator[Statement]]

}
