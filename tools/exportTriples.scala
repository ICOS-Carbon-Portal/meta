import scala.language.unsafeNulls
import scala.util.Using
import java.io.{BufferedOutputStream, FileOutputStream}
import java.nio.file.{Files, Paths}
import org.slf4j.LoggerFactory
import org.eclipse.rdf4j.rio.{Rio, RDFFormat}
import meta.tools.shared.replayRdfLog

private val log = LoggerFactory.getLogger("tools.exportTriples")

/*
	Export triples from RDFLog into .ttl files, one for each graph.
	NOTE: Requires large amounts of RAM, which means you most likely need:
		export SBT_OPTS="-Xmx12G -Xss2M"
*/
@main def exportTriples(args: String*): Unit = {
	import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}
	import se.lu.nateko.cp.meta.utils.rdf4j.asPlainScalaIterator
	import org.eclipse.rdf4j.model.impl.SimpleValueFactory

	require(args.nonEmpty, "Usage: exportTriples <outputDir> [instanceServerId]")

	val outputDir = Paths.get(args.head)
	Files.createDirectories(outputDir)

	val config = ConfigLoader.default
	val factory = SimpleValueFactory.getInstance()

	val allConfs = MetaDb.getAllInstanceServerConfigs(config.instanceServers)
	val selectedConfs = args.lift(1).fold(allConfs) { id =>
		allConfs.get(id).map(id -> _).toMap
	}

	for {
		(_id, conf) <- selectedConfs
		logName <- conf.logName
	} do {
		log.info(s"$logName: starting export")
		val memRepo = replayRdfLog(config.rdfLog, factory, logName)

		val outputFile = outputDir.resolve(s"$logName.nt")
		log.info(s"$logName: writing to $outputFile")

		Using.resource(new BufferedOutputStream(new FileOutputStream(outputFile.toFile))) { os =>
			Using.resource(memRepo.getConnection) { conn =>
				Using.resource(conn.getStatements(null, null, null)) { statements =>
					val writer = Rio.createWriter(RDFFormat.NTRIPLES, os)
					writer.startRDF()
					var written = 0
					statements.asPlainScalaIterator.foreach { statement =>
						writer.handleStatement(statement)
						written += 1
						if (written % 100000 == 0) {
							log.info(s"$logName: ${written / 1000}k statements written")
						}
					}
					writer.endRDF()
					log.info(s"$logName: $written statements exported to $outputFile")
				}
			}
		}
		memRepo.shutDown()
	}
	log.info("All graphs exported!")
}
