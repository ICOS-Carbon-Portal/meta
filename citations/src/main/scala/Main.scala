package se.lu.nateko.cp.meta.citations

import scala.language.unsafeNulls

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.event.Logging
import akka.stream.Materializer
import se.lu.nateko.cp.cpauth.core.ConfigLoader.appConfig
import se.lu.nateko.cp.meta.ConfigLoader
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.citation.CitationClient.{readCitCache, readDoiCache}

import scala.concurrent.ExecutionContext

/**
 * Entry point of the standalone citation-materialization service.
 *
 * This is the home of the materialization logic that used to run inside the
 * meta service: it populates the Virtuoso triplestore with the derived
 * citation triples and keeps them refreshed on a schedule.
 */
object Main:

	def main(args: Array[String]): Unit =
		given system: ActorSystem = ActorSystem("cpmetaCitations", config = appConfig)
		given ExecutionContext = system.dispatcher
		given Materializer = Materializer.matFromSystem(using system)
		val log = Logging.getLogger(system, this)

		val config = ConfigLoader.default
		given EnvriConfigs = config.core.envriConfigs

		val startup =
			for
				citCache <- readCitCache()
				doiCache <- readDoiCache()
			yield
				val service = new CitationMaterializationService(config, citCache, doiCache)
				CoordinatedShutdown(system).addTask(
					CoordinatedShutdown.PhaseServiceStop, "stop-citation-materializer"
				){() =>
					service.stop().map(_ => Done)
				}
				service.start()

		startup.failed.foreach: err =>
			log.error(err, "Could not start the citation materialization service")
			system.terminate()

end Main
