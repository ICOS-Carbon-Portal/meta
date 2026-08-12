package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import org.eclipse.rdf4j.repository.sail.SailRepository
import se.lu.nateko.cp.meta.{AppConfig, RdfStoreConfigLoader}
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.citation.CitationClient.{readCitCache, readDoiCache}
import se.lu.nateko.cp.meta.services.citation.CitationProviderFactory
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataService
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.persistence.RdfLogManager
import se.lu.nateko.cp.meta.services.sparql.magic.{CpNotifyingSail, GeoIndexProvider, IndexHandler, StorageSail}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Standalone owner of the persistent RDF4J store, Carbon Portal indexes, and
 * SPARQL protocol endpoint. `meta` connects with RDF4J's SPARQLRepository.
 */
object Main extends App:

	private val appConfig = AppConfig.rootConfWithWorkingDirOverrides
	private val metaConfig = RdfStoreConfigLoader.metaView
	private val storeConfig = RdfStoreConfigLoader.default
	private val host = storeConfig.httpBindInterface
	private val port = storeConfig.port

	private given system: ActorSystem = ActorSystem("cpmeta-rdf-store", appConfig)
	private given ExecutionContext = system.dispatcher
	private given EnvriConfigs = metaConfig.core.envriConfigs

	private val (isFreshInit, baseSail) = StorageSail(storeConfig.rdfStorage)

	private val startup = for
		(citCache, doiCache) <- readCitCache().zip(readDoiCache())
		citer = CitationProviderFactory(baseSail, citCache, doiCache, metaConfig)
		derivedMetadata = DerivedMetadataService(citer)
		indexFactories =
			if isFreshInit || storeConfig.rdfStorage.disableCpIndex then None
			else Some(IndexHandler(system.scheduler) -> GeoIndexProvider(using ExecutionContext.global))
		sail = CpNotifyingSail(baseSail, indexFactories, citer, derivedMetadata)
		logManager = RdfLogManager(storeConfig, baseSail.getValueFactory)
		repo = SailRepository(sail)
		_ = repo.init()
		_ = logManager.restore(repo, isFreshInit)
		indexData <- restoreIndex()
		_ <- sail.initSparqlMagicIndex(indexData)
		_ = if isFreshInit then sail.makeReadonly(
			"Fresh RDF-log restoration is complete; restart rdfStore for normal indexed operation"
		)
		queryServer = Rdf4jSparqlServer(repo, metaConfig.sparql)
		given ToResponseMarshaller[SparqlRequest] = queryServer.marshaller
		binding <- Http().newServerAt(host, port).bind(Route(
			repo,
			metaConfig.sparql,
			derivedMetadata,
			message => sail.makeReadonlyDumpIndexAndCaches(message)
		))
	yield (binding, queryServer, repo, logManager)

	startup.onComplete:
		case Success((binding, queryServer, repo, logManager)) =>
			system.log.info("RDF store listening on {}", binding.localAddress)
			sys.addShutdownHook:
				queryServer.shutdown()
				repo.shutDown()
				logManager.close()
				binding.unbind()
		case Failure(err) =>
			system.log.error(err, "Could not start RDF store")
			baseSail.shutDown()
			system.terminate()

	private def restoreIndex() =
		val conf = storeConfig.rdfStorage
		val recreate = isFreshInit || conf.recreateCpIndexAtStartup
		if recreate then IndexHandler.dropStorage()
		if recreate || conf.disableCpIndex then Future.successful(None)
		else IndexHandler.restore().map(Some(_)).recover:
			case err =>
				system.log.warning("Failed to restore SPARQL index: {}", err.getMessage)
				None
