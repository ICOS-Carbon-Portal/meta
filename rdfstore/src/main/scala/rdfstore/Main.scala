package se.lu.nateko.cp.meta.rdfstore

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import se.lu.nateko.cp.cpauth.core.ConfigLoader.appConfig
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.{ConfigLoader, CpmetaConfig}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext}

/** Runs the RDF store as a standalone service, which meta reaches over HTTP */
object Main extends App:

	given system: ActorSystem = ActorSystem("cpmeta-rdf-store", appConfig)
	private val log = Logging.getLogger(system, this)
	private given ExecutionContext = system.dispatcher

	val config: CpmetaConfig = ConfigLoader.default

	val startup = for
		store <- RdfStore.start(config)
		_ = if store.isFreshInit then store.makeReadonly("This was a fresh RDF store initialization, running in " +
			"readonly mode; restart rdfStore for proper operation")
		_ <- store.initSparqlMagicIndex()
		route =
			given ToResponseMarshaller[SparqlQuery] = store.sparqlServer.marshaller
			Route(
				store.repo, store.vanillaSparqlServer, config.sparql, store.derivedMetadata, store.makeReadonlyDumpIndexAndCaches
			)
		//updates from meta only respond once committed, so a timeout would report a large but successful update as failed
		binding <- Http().newServerAt(config.rdfStore.httpBindInterface, config.rdfStore.port)
			.adaptSettings(_.mapTimeouts(_.withRequestTimeout(Duration.Inf)))
			.bind(route)
	yield
		sys.addShutdownHook:
			try Await.result(binding.unbind(), 10.seconds)
			finally
				store.close()
				println("RDF store has been shut down")
		log.info(binding.toString)

	startup.failed.foreach: err =>
		log.error(err, "Could not start the RDF store")
		system.terminate()
