package se.lu.nateko.cp.meta.api

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import se.lu.nateko.cp.meta.rdfstore.{Quota, SparqlDataset}

case class SparqlQuery(
	query: String,
	quota: Quota,
	dataset: SparqlDataset = SparqlDataset()
)

trait SparqlServer:
	/**
	 * Executes SPARQL SELECT, CONSTRUCT, DESCRIBE queries
	 * Serializes the query results to one of the standard formats, depending on HTTP content negotiation
	 */
	def marshaller: ToResponseMarshaller[SparqlQuery]
	def shutdown(): Unit
