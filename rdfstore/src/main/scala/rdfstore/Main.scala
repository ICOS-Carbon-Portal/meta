package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import org.eclipse.rdf4j.repository.sail.SailRepository
import se.lu.nateko.cp.meta.{AppConfig, RdfStoreConfigLoader, SchemaOntologyConfig}
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.ingestion.{BnodeStabilizers, Ingestion, RdfXmlFileIngester}
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataService
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.persistence.RdfLogManager
import se.lu.nateko.cp.meta.services.sparql.magic.{CpNotifyingSail, GeoIndexProvider, IndexHandler, StorageSail}
import se.lu.nateko.cp.meta.utils.rdf4j.*

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

	private val startup = {
		val (isFreshInit, baseSail) = StorageSail(storeConfig.rdfStorage)
		val citer = CitationProvider(baseSail, citationStoreConfig)
		val derivedMetadata = DerivedMetadataService(citer)
		val indexFactories =
			if storeConfig.rdfStorage.disableCpIndex then None
			else Some(IndexHandler(system.scheduler) -> GeoIndexProvider(using ExecutionContext.global))
		val sail = CpNotifyingSail(baseSail, indexFactories, citer, derivedMetadata)
		val logManager = RdfLogManager(
			RdfStoreConfigLoader.rdfLogConfig,
			citationStoreConfig.instanceServers,
			baseSail.getValueFactory
		)
		val repo = SailRepository(sail)
		repo.init()
		logManager.restore(repo, isFreshInit)
		val schemaOntologiesIngested = ingestSchemaOntologies(repo, storeConfig.schemaOntologies)
		for {
			_ <- schemaOntologiesIngested
			_ <- sail.initSparqlMagicIndex()
			queryServer = Rdf4jSparqlServer(repo, sparqlConfig)
			given ToResponseMarshaller[SparqlRequest] = queryServer.marshaller
			binding <- Http().newServerAt(host, port).bind(Route(repo, sparqlConfig, derivedMetadata))
		}
		yield (binding, queryServer, repo, logManager)
	}

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
			system.terminate()

	/**
	 * The store's OWL schema graphs (cpmeta, stationEntry, otcmeta, …). Unlike instance
	 * data, they are not covered by the rdf log, so rdfStore must (re)ingest them itself
	 * from the classpath on every startup, before serving any SPARQL queries.
	 */
	private def ingestSchemaOntologies(
		repo: SailRepository, configs: Seq[SchemaOntologyConfig]
	)(using ExecutionContext): Future[Unit] =
		given valueFactory: org.eclipse.rdf4j.model.ValueFactory = repo.getValueFactory
		given BnodeStabilizers = new BnodeStabilizers
		Future.sequence(configs.map{ conf =>
			val writeContext = conf.writeContext.toRdf
			val target = new Rdf4jInstanceServer(repo, writeContext)
			Ingestion.ingest(target, new RdfXmlFileIngester(conf.owlResource), valueFactory).andThen:
				case Success(_) => system.log.info("ingested schema ontology into {}", writeContext)
				case Failure(err) => system.log.error(err, "failed to ingest schema ontology into {}", writeContext)
		}).map(_ => ())
