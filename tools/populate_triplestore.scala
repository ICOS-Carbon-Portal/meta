import scala.language.unsafeNulls

import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.services.sparql.RemoteRepository
import se.lu.nateko.cp.meta.utils.rdf4j.{toRdf, asPlainScalaIterator, accessEagerly, transact}
import org.eclipse.rdf4j.model.ValueFactory

private val log = LoggerFactory.getLogger("tools.populateTriplestore")

private val ChunkSize = 10

@main def populateTriplestore(_args: String*): Unit =
	val config = ConfigLoader.default
	val repo = RemoteRepository.apply(config.rdfStorage)
	try
		given factory: ValueFactory = repo.getValueFactory

		val allConfs = MetaDb.getAllInstanceServerConfigs(config.instanceServers)
		val selectedConfs = _args.headOption.fold(allConfs): id =>
			allConfs.get(id).map(id -> _).toMap

		for {
			(_id, conf) <- selectedConfs
			logName <- conf.logName
		} do {
			val rdfLog = PostgresRdfLog(logName, config.rdfLog, factory)
			log.info(s"Ingesting from RDF log $logName into memory...")
			val memRepo = RdfUpdateLogIngester.ingestIntoMemory(rdfLog.updates)
			try
				val ctx = conf.writeContext.toRdf
				RdfUpdateLogIngester.clean(repo, ctx).get
				val statements = memRepo.accessEagerly(conn =>
					conn.getStatements(null, null, null, false).asPlainScalaIterator.toVector
				)
				log.info(s"Writing ${statements.size} statements from $logName to remote store...")
				statements.grouped(ChunkSize).foreach(chunk =>
					repo.transact(conn => chunk.foreach(st => conn.add(st, ctx))).get
				)
				log.info(s"Ingesting from RDF log $logName done!")
			finally
				memRepo.shutDown()
		}
	finally
		repo.shutDown()
