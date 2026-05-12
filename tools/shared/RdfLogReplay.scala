package meta.tools

import scala.language.unsafeNulls
import scala.util.Using

import org.slf4j.LoggerFactory
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.repository.sail.SailRepository
import se.lu.nateko.cp.meta.RdflogConfig
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.utils.rdf4j.Loading

private val log = LoggerFactory.getLogger("tools.RdfLogReplay")

def replayRdfLog(rdfLogConfig: RdflogConfig, factory: ValueFactory, logName: String): SailRepository = {
	val rdfLog = PostgresRdfLog(logName, rdfLogConfig, factory)
	log.info(s"$logName: replaying updates into in-memory repository")
	val memRepo = Loading.emptyInMemory
	Using.resource(rdfLog.updates) { updates =>
		var replayed = 0
		Using.resource(memRepo.getConnection) { conn =>
			updates.foreach { update =>
				if (update.isAssertion) conn.add(update.statement)
				else conn.remove(update.statement)
				replayed += 1
				if (replayed % 100000 == 0) {
					log.info(s"$logName: ${replayed / 1000}k updates replayed")
				}
			}
		}
		log.info(s"$logName: $replayed updates replayed")
	}
	memRepo
}
