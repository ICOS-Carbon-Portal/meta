package se.lu.nateko.cp.meta.api

import akka.http.scaladsl.marshalling.ToResponseMarshaller

case class SparqlRequest(query: String, clientId: Option[String] = None)

trait SparqlServer:
	/**
	 * Executes SPARQL SELECT, CONSTRUCT, DESCRIBE queries
	 * Serializes the query results to one of the standard formats, depending on HTTP content negotiation
	 */
	def marshaller: ToResponseMarshaller[SparqlRequest]
	def shutdown(): Unit
