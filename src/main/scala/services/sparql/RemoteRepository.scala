package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.slf4j.LoggerFactory

case class RemoteRepositoryConfig(
	sparqlEndpoint: String,
	updateEndpoint: Option[String],
	username: Option[String],
	password: Option[String]
)

object RemoteRepository:
	private val log = LoggerFactory.getLogger(getClass())

	def apply(conf: RemoteRepositoryConfig): Repository =
		val updateUrl = conf.updateEndpoint.getOrElse(conf.sparqlEndpoint)
		val repo = new SPARQLRepository(conf.sparqlEndpoint, updateUrl)
		for
			user <- conf.username
			pass <- conf.password
		do repo.setUsernameAndPassword(user, pass)
		repo.init()
		log.info(s"SPARQLRepository initialised against ${conf.sparqlEndpoint}")
		repo

end RemoteRepository
