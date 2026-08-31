package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import se.lu.nateko.cp.meta.{AppConfig, RdfStoreConfigLoader}
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataService
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.persistence.RdfLogManager
import se.lu.nateko.cp.meta.services.sparql.magic.{CpNotifyingSail, GeoIndexProvider, IndexHandler, StorageSail}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success}

/**
 * Standalone owner of the persistent RDF4J store, Carbon Portal indexes, and
 * SPARQL protocol endpoint. `meta` connects with RDF4J's SPARQLRepository.
 *
 * Started with `--restore`, the process performs a one-off RDF-log replay and exits instead of
 * serving; see [[restoreAndExit]].
 */
object Main extends App:

	private val cliOptions = CliOptions.parse(args.toIndexedSeq) match
		case Right(options) => options
		case Left(errorMsg) =>
			Console.err.println(errorMsg)
			Console.err.println(CliOptions.usage)
			sys.exit(1)

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
		val baseSail = StorageSail(storeConfig.rdfStorage, bulkLoad = cliOptions.restoreFromRdfLog)
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
		if (cliOptions.restoreFromRdfLog) {
			restoreAndExit(repo, logManager)
		} else {
			for {
				_ <- sail.initSparqlMagicIndex()
				queryServer = Rdf4jSparqlServer(repo, sparqlConfig)
				given ToResponseMarshaller[SparqlRequest] = queryServer.marshaller
				binding <- Http().newServerAt(host, port).bind(Route(repo, sparqlConfig, derivedMetadata))
			}
			yield (binding, queryServer, repo, logManager)
		}
	}

	/**
	 * One-off RDF-log replay: replays the configured logs, closes the store and exits, without
	 * building the custom indexes or binding the HTTP endpoints, so a partially restored store is
	 * never queryable. The indexes are not persisted, so the next, normally started process
	 * rebuilds them from the restored store.
	 *
	 * The run exits rather than going on to serve because it opened the storage for bulk ingest
	 * (see StorageSail's `bulkLoad`): that is safe for a re-runnable replay, but a serving process
	 * must reopen the storage with durable settings.
	 */
	private def restoreAndExit(repo: Repository, logManager: RdfLogManager): Nothing =
		system.log.info("Restoring the store from the RDF logs, as requested by {}", CliOptions.RestoreFlag)
		val exitCode =
			try
				val result = logManager.restore(repo, restoreRequested = true)
				system.log.info(
					"Replayed {} RDF log(s). Restart without {} to serve the store.",
					result.attemptedLogs,
					CliOptions.RestoreFlag
				)
				0
			catch case err: Throwable =>
				system.log.error(err, "RDF-log restoration failed, the store is left incomplete")
				1
			finally
				logManager.close()
				repo.shutDown() // flushes and syncs the storage files
		Await.ready(system.terminate(), 30.seconds)
		sys.exit(exitCode)

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
