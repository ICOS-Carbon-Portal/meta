package se.lu.nateko.cp.meta.citations

import scala.language.unsafeNulls

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.Materializer
import se.lu.nateko.cp.cpauth.core.ConfigLoader.appConfig
import se.lu.nateko.cp.meta.ConfigLoader
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.citation.CitationClient.{readCitCache, readDoiCache}
import se.lu.nateko.cp.meta.services.citation.{CitationMaterializer, CitationProvider}
import se.lu.nateko.cp.meta.services.sparql.VirtuosoRepository

import scala.concurrent.ExecutionContext

/**
 * Entry point of the standalone citations service.
 *
 * It owns all citation computation that used to run inside the meta service:
 *   - it materializes the derived citation triples into Virtuoso on a schedule, and
 *   - it serves freshly-computed objects/collections over HTTP so meta's
 *     DOI-minting path can mint against non-stale citation metadata.
 */
object Main {

	def main(args: Array[String]): Unit = {
		given system: ActorSystem = ActorSystem("cpmetaCitations", config = appConfig)
		given ExecutionContext = system.dispatcher
		given Materializer = Materializer.matFromSystem(using system)
		val log = Logging.getLogger(system, this)

		val config = ConfigLoader.default
		given EnvriConfigs = config.core.envriConfigs

		val startup =
			for {
				citCache <- readCitCache()
				doiCache <- readDoiCache()
			}
			yield {
				val repo = new VirtuosoRepository(config.virtuoso)
				val citer = CitationProvider(repo, citCache, doiCache, config)
				val materializer = new CitationMaterializer(repo, citer, config.citations.derivedCitationsGraph)

				val matService = CitationMaterializationService(materializer, config.citations)
				matService.start()

				val route = new CitationRouting(citer).route
				val bindingFut = Http().newServerAt(config.httpBindInterface, config.citations.servicePort).bind(route)

				CoordinatedShutdown(system).addJvmShutdownHook {
					matService.stop()
					repo.shutDown()
				}

				bindingFut.foreach(b => log.info(s"Citations service listening on ${b.localAddress}"))
			}

		startup.failed.foreach { err =>
			log.error(err, "Could not start the citations service")
			system.terminate()
		}
	}

} // end Main
