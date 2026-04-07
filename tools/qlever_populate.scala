import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.{CpmetaConfig, MetaDb}
import se.lu.nateko.cp.meta.persistence.QleverUpdateLogIngester
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.services.sparql.QleverClient
import tools.shared.config.cpmetaConfig

import java.net.URI
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

private val log = LoggerFactory.getLogger("devtools.qleverPopulate")

@main def qleverPopulate(args: String*) =

	log.info("Loading configuration...")
	val config = cpmetaConfig

	val filterNames = args.toSet
	val targets = collectTargets(config, filterNames)

	if targets.isEmpty then
		log.warn("No matching RDF logs found to ingest.")
		if filterNames.nonEmpty then
			log.warn(s"Filter was: ${filterNames.mkString(", ")}")
		System.exit(1)

	log.info(s"Will ingest ${targets.size} RDF log(s) into QLever at ${config.qlever.endpoint}")
	targets.foreach((name, ctx) => log.info(s"  - $name -> $ctx"))

	given system: ActorSystem = ActorSystem("qleverPopulate")
	given Materializer = Materializer(system)

	try
		val qleverClient = new QleverClient(config.qlever)
		val factory = SimpleValueFactory.getInstance().nn
		var succeeded = 0
		var failed = 0

		for (logName, writeCtxUri) <- targets do
			log.info(s"[$logName] Starting ingestion into <$writeCtxUri>...")
			val writeContext = factory.createIRI(writeCtxUri.toString)
			val rdfLog = PostgresRdfLog(logName, config.rdfLog, factory)
			QleverUpdateLogIngester.ingest(rdfLog.updates, qleverClient, true, writeContext) match
				case Success(_) =>
					succeeded += 1
					log.info(s"[$logName] Ingestion complete.")
				case Failure(err) =>
					failed += 1
					log.error(s"[$logName] Ingestion failed: ${err.getMessage}", err)

		log.info(s"Finished. $succeeded succeeded, $failed failed out of ${targets.size} total.")
	finally
		Await.result(system.terminate(), 30.seconds)
		log.info("ActorSystem terminated.")

private def collectTargets(config: CpmetaConfig, filterNames: Set[String]): Seq[(String, URI)] =
	val allConfigs = MetaDb.getAllInstanceServerConfigs(config.instanceServers)
	val withLogs = allConfigs.values
		.filter(_.logName.isDefined)
		.map(c => (c.logName.get, c.writeContext))
		.toSeq
		.distinct
	if filterNames.isEmpty then withLogs
	else withLogs.filter((name, _) => filterNames.contains(name))
