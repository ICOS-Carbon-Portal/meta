package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import java.net.URI

/** Where rdfStore listens; the meta service derives its rdfStore endpoints from it */
case class RdfStoreConfig(httpBindInterface: String, port: Int):
	private def baseUrl: URI = new URI(s"http://$httpBindInterface:$port")
	private[rdfstore] def sparqlEndpoint: URI = baseUrl.resolve("/internal/sparql")
	private[rdfstore] def vanillaSparqlEndpoint: URI = baseUrl.resolve("/internal/vanilla/sparql")
	private[rdfstore] def adminEndpoint: URI = baseUrl.resolve("/admin/read-only")
	private[rdfstore] def derivedMetadataEndpoint: URI = baseUrl.resolve("/internal/derived/v1/resolve")
	private[rdfstore] def dropDoiCacheEndpoint(doi: String): URI = baseUrl.resolve("/internal/derived/v1/drop-cache/" + doi)
