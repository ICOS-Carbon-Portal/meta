package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClients}
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.RdfStorageConfig

class RemoteRepository(conf: RdfStorageConfig) extends SPARQLRepository(conf.sparqlEndpoint, conf.updateEndpoint) {
	private val log = LoggerFactory.getLogger(getClass())

	private val httpClient = {
		val credsProvider = new BasicCredentialsProvider()
		credsProvider.setCredentials(
			AuthScope.ANY,
			new UsernamePasswordCredentials(conf.username, conf.password)
		)

		HttpClients.custom()
			.setDefaultCredentialsProvider(credsProvider)
			.build()
	}

	setHttpClient(httpClient)
	super.init()
	log.info(s"RemoteRepository initialised against ${conf.sparqlEndpoint} and ${conf.updateEndpoint}")

	override def shutDownInternal(): Unit = {
		try super.shutDownInternal()
		finally httpClient.close()
	}
}
