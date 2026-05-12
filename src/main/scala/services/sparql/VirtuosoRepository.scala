package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClients}
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

	override def shutDownInternal(): Unit = {
		try { super.shutDownInternal() }
		finally { httpClient.close() }
	}
}

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
