import scala.language.unsafeNulls

import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.services.sparql.RemoteRepository
import se.lu.nateko.cp.meta.utils.rdf4j.toRdf
import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester

private val log = LoggerFactory.getLogger("tools.populateTriplestore")

@main def populateTriplestore(args: String*): Unit =
	val config = ConfigLoader.default
	val repo = RemoteRepository.apply(config.rdfStorage)
	given factory: ValueFactory = repo.getValueFactory

	val allConfs = MetaDb.getAllInstanceServerConfigs(config.instanceServers)
	val selectedConfs = args.headOption.fold(allConfs): id =>
		allConfs.get(id).map(id -> _).toMap

	for {
		(_id, conf) <- selectedConfs
		logName <- conf.logName
	} do {
		val rdfLog = PostgresRdfLog(logName, config.rdfLog, factory)
		val ctx = conf.writeContext.toRdf
		log.info(s"$logName: Starting ingestion")
		RdfUpdateLogIngester.ingest(rdfLog.updates, repo, 100, false, ctx).get
		log.info(s"$logName: done!")
	}
	repo.shutDown()
	println(s"ALL DONE!")
