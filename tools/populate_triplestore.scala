import scala.language.unsafeNulls

import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.services.sparql.RemoteRepository
import se.lu.nateko.cp.meta.utils.rdf4j.toRdf
import org.eclipse.rdf4j.model.ValueFactory

private val log = LoggerFactory.getLogger("tools.populateTriplestore")

@main def populateTriplestore(_args: String*): Unit =
	val config = ConfigLoader.default
	val repo = RemoteRepository.apply(config.rdfStorage)
	try
		given factory: ValueFactory = repo.getValueFactory

		for {
			(_id, conf) <- MetaDb.getAllInstanceServerConfigs(config.instanceServers)
			logName <- conf.logName
		} do {
			val rdfLog = PostgresRdfLog(logName, config.rdfLog, factory)
			log.info(s"Ingesting from RDF log $logName...")
			RdfUpdateLogIngester.ingest(rdfLog.updates, repo, true, conf.writeContext.toRdf)
			log.info(s"Ingesting from RDF log $logName done!")
		}
	finally
		repo.shutDown()
