import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.{CpmetaConfig, MetaDb}
import se.lu.nateko.cp.meta.persistence.NTriplesFileExporter
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import tools.shared.config.cpmetaConfig

import java.net.URI
import java.nio.file.{Files, Path}
import scala.util.{Failure, Success}

private val log = LoggerFactory.getLogger("devtools.ntExport")

@main def exportTriples(outputDir: String, args: String*) =

	log.info("Loading configuration...")
	val config = cpmetaConfig

	val outPath = Path.of(outputDir)
	Files.createDirectories(outPath)

	val filterNames = args.toSet
	val targets = collectTargets(config, filterNames)

	if targets.isEmpty then
		log.warn("No matching RDF logs found to export.")
		if filterNames.nonEmpty then
			log.warn(s"Filter was: ${filterNames.mkString(", ")}")
		System.exit(1)

	log.info(s"Will export ${targets.size} RDF log(s) to N-Triples files in $outPath")
	targets.foreach((name, ctx) => log.info(s"  - $name -> $ctx"))

	val factory = SimpleValueFactory.getInstance().nn
	var succeeded = 0

	for (logName, writeCtxUri) <- targets do
		val outputFile = outPath.resolve(s"$logName.nt")
		log.info(s"[$logName] Exporting to $outputFile ...")
		val writeContext = factory.createIRI(writeCtxUri.toString)
		val rdfLog = PostgresRdfLog(logName, config.rdfLog, factory)
		NTriplesFileExporter.writeNTriples(rdfLog.updates, outputFile, writeContext) match
			case Success(count) =>
				succeeded += 1
				log.info(s"[$logName] Export complete: $count triples written.")
			case Failure(err) =>
				log.error(s"[$logName] Export failed: ${err.getMessage}", err)
				log.info(s"Finished. $succeeded succeeded, failed on $logName out of ${targets.size} total.")

	log.info(s"Finished. $succeeded succeeded out of ${targets.size} total.")

private def collectTargets(config: CpmetaConfig, filterNames: Set[String]): Seq[(String, URI)] =
	val allConfigs = MetaDb.getAllInstanceServerConfigs(config.instanceServers)
	val withLogs = allConfigs.values
		.filter(_.logName.isDefined)
		.map(c => (c.logName.get, c.writeContext))
		.toSeq
		.distinct
	if filterNames.isEmpty then withLogs
	else withLogs.filter((name, _) => filterNames.contains(name))
