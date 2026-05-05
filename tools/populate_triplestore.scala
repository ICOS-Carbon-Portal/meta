import scala.language.unsafeNulls

import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.services.sparql.RemoteRepository
import se.lu.nateko.cp.meta.utils.rdf4j.toRdf

private val log = LoggerFactory.getLogger("tools.populateTriplestore")

@main def populateTriplestore(args: String*): Unit =

	val fromId: Option[Int] = args.headOption.map(_.toInt)

	val config = ConfigLoader.default
	val repo = RemoteRepository.apply(config.rdfStorage)

	try
		val factory = repo.getValueFactory
		given org.eclipse.rdf4j.model.ValueFactory = factory

		val allConfigs = MetaDb.getAllInstanceServerConfigs(config.instanceServers)

		for (id, conf) <- allConfigs; logName <- conf.logName do
			val writeContext = conf.writeContext.toRdf
			val rdfLog = PostgresRdfLog(logName, config.rdfLog, factory)
			val cleanFirst = fromId.isEmpty
			val updates = fromId.fold(rdfLog.updates)(rdfLog.updatesFromId)
			val detail = fromId.fold("")(id => s" starting from id $id")
			log.info(s"Ingesting from RDF log $logName$detail...")
			RdfUpdateLogIngester.ingest(updates, repo, cleanFirst, writeContext)
			log.info(s"Ingesting from RDF log $logName done!")
	finally
		repo.shutDown()
