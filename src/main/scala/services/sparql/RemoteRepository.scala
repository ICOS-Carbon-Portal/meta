package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClients}
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.RdfStorageConfig

object RemoteRepository:
	private val log = LoggerFactory.getLogger(getClass())

	def apply(conf: RdfStorageConfig): Repository = {
		val credsProvider = new BasicCredentialsProvider()
		credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(conf.username, conf.password))
		val httpClient = HttpClients.custom()
			.setDefaultCredentialsProvider(credsProvider)
			.build()

		val repo = new SPARQLRepository(conf.sparqlEndpoint, conf.updateEndpoint)
		repo.setHttpClient(httpClient)
		repo.init()
		log.info(s"SPARQLRepository initialised against ${conf.sparqlEndpoint} and ${conf.updateEndpoint}")
		repo
	}

end RemoteRepository
