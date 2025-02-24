package se.lu.nateko.cp.meta.api

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.query.BindingSet

final case class SparqlQuery(query: String, clientId: Option[String] = None)

trait SparqlServer:
	/**
	 * Executes SPARQL SELECT, CONSTRUCT, DESCRIBE queries
	 * Serializes the query results to one of the standard formats, depending on HTTP content negotiation
	 */
	def marshaller: ToResponseMarshaller[SparqlQuery]
	def shutdown(): Unit


trait SparqlRunner:
	def evaluateGraphQuery(q: SparqlQuery): CloseableIterator[Statement]
	def evaluateTupleQuery(q: SparqlQuery): CloseableIterator[BindingSet]
