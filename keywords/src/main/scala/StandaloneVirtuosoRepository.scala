package se.lu.nateko.cp.meta.keyword

import scala.language.unsafeNulls

import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.{BasicCredentialsProvider, CloseableHttpClient, HttpClients}
import org.eclipse.rdf4j.http.client.SPARQLProtocolSession
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository

/**
 * The Virtuoso triplestore as an rdf4j repository, over its plain and authenticated SPARQL
 * endpoints, self-contained so that [[KeywordsApp]] needs nothing of the meta application.
 */
private[keyword] final class StandaloneVirtuosoRepository(host: String, username: String, password: String)
		extends SPARQLRepository(s"$host/sparql", s"$host/sparql-auth"):

	private val httpClient: CloseableHttpClient =
		val credentials = new BasicCredentialsProvider()
		credentials.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))
		HttpClients.custom().setDefaultCredentialsProvider(credentials).setMaxConnPerRoute(32).setMaxConnTotal(64).build()

	setHttpClient(httpClient)
	super.init()

	override def createSPARQLProtocolSession(): SPARQLProtocolSession =
		val session = super.createSPARQLProtocolSession()
		session.setPreferredTupleQueryResultFormat(TupleQueryResultFormat.JSON)
		session

	override protected def shutDownInternal(): Unit =
		try super.shutDownInternal()
		finally httpClient.close()
