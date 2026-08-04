package se.lu.nateko.cp.rdfstore

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import com.typesafe.config.ConfigFactory
import org.eclipse.rdf4j.repository.sail.SailRepository
import se.lu.nateko.cp.meta.ConfigLoader
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.citation.CitationClient.{readCitCache, readDoiCache}
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.services.sparql.magic.{CpNotifyingSail, GeoIndexProvider, IndexHandler, StorageSail}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Standalone owner of the persistent RDF4J store, Carbon Portal indexes, and
 * SPARQL protocol endpoint. `meta` connects with RDF4J's SPARQLRepository.
 */
object Main extends App:

	private val appConfig = ConfigFactory.load()
	private val host = appConfig.getString("rdfStore.httpBindInterface")
	private val port = appConfig.getInt("rdfStore.port")
	private val metaConfig = ConfigLoader.default

	private given system: ActorSystem = ActorSystem("cpmeta-rdf-store", appConfig)
	private given ExecutionContext = system.dispatcher
	private given EnvriConfigs = metaConfig.core.envriConfigs

	private val (isFreshInit, baseSail) = StorageSail(metaConfig.rdfStorage)

	private val startup = for
		(citCache, doiCache) <- readCitCache().zip(readDoiCache())
		citer = CitationProvider(baseSail, citCache, doiCache, metaConfig)
		indexFactories =
			if isFreshInit || metaConfig.rdfStorage.disableCpIndex then None
			else Some(IndexHandler(system.scheduler) -> GeoIndexProvider(using ExecutionContext.global))
		sail = CpNotifyingSail(baseSail, indexFactories, citer)
		repo = SailRepository(sail)
		_ = repo.init()
		indexData <- restoreIndex()
		_ <- sail.initSparqlMagicIndex(indexData)
		_ = if isFreshInit then sail.makeReadonly(
			"Fresh standalone RDF store has no metadata bootstrap; restore/migrate the store and restart"
		)
		queryServer = Rdf4jSparqlServer(repo, metaConfig.sparql)
		given ToResponseMarshaller[SparqlQuery] = queryServer.marshaller
		binding <- Http().newServerAt(host, port).bind(Route(repo))
	yield (binding, queryServer, repo)

	startup.onComplete:
		case Success((binding, queryServer, repo)) =>
			system.log.info("RDF store listening on {}", binding.localAddress)
			sys.addShutdownHook:
				queryServer.shutdown()
				repo.shutDown()
				binding.unbind()
		case Failure(err) =>
			system.log.error(err, "Could not start RDF store")
			baseSail.shutDown()
			system.terminate()

	private def restoreIndex() =
		val conf = metaConfig.rdfStorage
		val recreate = isFreshInit || conf.recreateCpIndexAtStartup
		if recreate then IndexHandler.dropStorage()
		if recreate || conf.disableCpIndex then Future.successful(None)
		else IndexHandler.restore().map(Some(_)).recover:
			case err =>
				system.log.warning("Failed to restore SPARQL index: {}", err.getMessage)
				None
