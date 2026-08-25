package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import org.eclipse.rdf4j.repository.sail.SailRepository
import se.lu.nateko.cp.meta.{AppConfig, RdfStoreConfigLoader}
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.citation.CitationProvider
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
	private val citationStoreConfig = RdfStoreConfigLoader.citationStoreConfig
	private val sparqlConfig = RdfStoreConfigLoader.sparqlConfig
	private val storeConfig = RdfStoreConfigLoader.default
	private val host = storeConfig.httpBindInterface
	private val port = storeConfig.port

	private given system: ActorSystem = ActorSystem("cpmeta-rdf-store", appConfig)
	private given ExecutionContext = system.dispatcher
	private given EnvriConfigs = citationStoreConfig.core.envriConfigs

	private val startup = for
		(isFreshInit, baseSail) <- Future(StorageSail(storeConfig.rdfStorage))
		citer = CitationProvider(baseSail, citationStoreConfig)
		derivedMetadata = DerivedMetadataService(citer)
		indexFactories =
			if storeConfig.rdfStorage.disableCpIndex then None
			else Some(IndexHandler(system.scheduler) -> GeoIndexProvider(using ExecutionContext.global))
		sail = CpNotifyingSail(baseSail, indexFactories, citer, derivedMetadata)
		logManager = RdfLogManager(
			RdfStoreConfigLoader.rdfLogConfig,
			citationStoreConfig.instanceServers,
			baseSail.getValueFactory
		)
		repo = SailRepository(sail)
		_ = repo.init()
		_ = logManager.restore(repo, isFreshInit)
		_ <- sail.initSparqlMagicIndex()
		queryServer = Rdf4jSparqlServer(repo, sparqlConfig)
		given ToResponseMarshaller[SparqlRequest] = queryServer.marshaller
		binding <- Http().newServerAt(host, port).bind(Route(repo, sparqlConfig, derivedMetadata))
	yield (binding, queryServer, repo, logManager, baseSail)

	startup.onComplete:
		case Success((binding, queryServer, repo, logManager, _)) =>
			system.log.info("RDF store listening on {}", binding.localAddress)
			sys.addShutdownHook:
				queryServer.shutdown()
				repo.shutDown()
				logManager.close()
				binding.unbind()
		case Failure(err) =>
			system.log.error(err, "Could not start RDF store")
			system.terminate()
