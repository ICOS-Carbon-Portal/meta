package se.lu.nateko.cp.meta.api

import akka.http.scaladsl.marshalling.ToResponseMarshaller

case class SparqlQuery(query: String, clientId: Option[String] = None)

trait SparqlServer:
	/**
	 * Executes SPARQL SELECT, CONSTRUCT, DESCRIBE queries
	 * Serializes the query results to one of the standard formats, depending on HTTP content negotiation
	 */
	def marshaller: ToResponseMarshaller[SparqlQuery]
	def shutdown(): Unit
