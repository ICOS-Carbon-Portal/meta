package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClients}
import org.eclipse.rdf4j.http.client.SPARQLProtocolSession
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.VirtuosoConfig
import org.apache.http.impl.client.CloseableHttpClient

final class VirtuosoRepository(conf: VirtuosoConfig)
	 extends SPARQLRepository(s"${conf.host}/sparql", s"${conf.host}/sparql-auth") {

	private val log = LoggerFactory.getLogger(getClass())
	private val httpClient = makeHttpClient(conf)

	setHttpClient(httpClient)
	super.init()
	log.info(s"Initialised against ${conf.host}")

	// Request SPARQL/JSON instead of the default SPARQL/XML for tuple results.
	// This avoids SAXParseException when the result contains control characters, which
	// apparently some of our data does. In JSON, they are properly escaped.
	override def createSPARQLProtocolSession(): SPARQLProtocolSession = {
		val session = super.createSPARQLProtocolSession()
		session.setPreferredTupleQueryResultFormat(TupleQueryResultFormat.JSON)
		session
	}

	override def shutDownInternal(): Unit = {
		try { super.shutDownInternal() }
		finally { httpClient.close() }
	}
}

// We need to set a custom HTTP client that simply does Basic auth
// in order for Virtuoso interaction to work.
// As far as I understand, the difference is that a custom client sends authorization
// when challenged, while default behaviour is to send a pre-emptive auth.
private def makeHttpClient(conf: VirtuosoConfig): CloseableHttpClient = {
	val credsProvider = new BasicCredentialsProvider()
	credsProvider.setCredentials(
		AuthScope.ANY,
		new UsernamePasswordCredentials(conf.username, conf.password)
	)

	HttpClients.custom()
		.setDefaultCredentialsProvider(credsProvider)
		.build()
}
