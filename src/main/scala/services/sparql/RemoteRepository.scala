package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.RdfStorageConfig

object RemoteRepository:
	private val log = LoggerFactory.getLogger(getClass())

	def apply(conf: RdfStorageConfig): Repository = {
		val repo = new SPARQLRepository(conf.sparqlEndpoint, conf.updateEndpoint)
		repo.setUsernameAndPassword(conf.username, conf.password)
		repo.init()
		log.info(s"SPARQLRepository initialised against ${conf.sparqlEndpoint}")
		repo
	}

end RemoteRepository
