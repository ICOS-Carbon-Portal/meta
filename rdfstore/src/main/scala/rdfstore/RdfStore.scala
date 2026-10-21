package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.Materializer
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import se.lu.nateko.cp.meta.{CpmetaConfig, IngestionMode}
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.ingestion.{BnodeStabilizers, Ingestion, RdfXmlFileIngester}
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.persistence.RdfLogManager
import se.lu.nateko.cp.meta.services.citation.CitationClient.{readCitCache, readDoiCache}
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
import se.lu.nateko.cp.meta.services.sparql.magic.{CpNotifyingSail, GeoIndexProvider, IndexHandler, StorageSail}
import se.lu.nateko.cp.meta.utils.rdf4j.toRdf

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** The RDF storage with its citations, derived metadata, SPARQL magic index and SPARQL endpoint */
final class RdfStore private (
	val repo: SailRepository,
	val citer: CitationProvider,
	val derivedMetadata: DerivedMetadataService,
	val sparqlServer: Rdf4jSparqlServer,
	/** The storage was created from scratch at this startup, and has been restored from the RDF logs */
	val isFreshInit: Boolean,
	sail: CpNotifyingSail,
	indexData: Future[Option[IndexData]],
	logManager: RdfLogManager
)(using ExecutionContext) extends AutoCloseable:

	/** Completes when the index is initialized; the geo index keeps building in the background */
	/** The repository with only the stored statements, without the SPARQL magic index or derived metadata */
	def vanillaRepo: Repository = citer.repo

	def initSparqlMagicIndex(): Future[Unit] = indexData.map(idx => sail.initSparqlMagicIndex(idx): Unit)

	def makeReadonly(errorMessage: String): Unit = sail.makeReadonly(errorMessage)

	def makeReadonlyDumpIndexAndCaches(errorMessage: String): Future[String] =
		sail.makeReadonlyDumpIndexAndCaches(errorMessage)

	override def close(): Unit =
		sparqlServer.shutdown()
		repo.shutDown()
		logManager.close()

end RdfStore

object RdfStore:

	/** Opens the storage, restores the configured RDF logs and ingests the schema ontologies.
	  * Restoring the SPARQL magic index starts here, but it is only initialized by `initSparqlMagicIndex`.
	  */
	def start(config: CpmetaConfig)(using system: ActorSystem, mat: Materializer): Future[RdfStore] =
		val log = Logging.getLogger(system, this)
		given ExecutionContext = system.dispatcher
		given EnvriConfigs = config.core.envriConfigs

		val indexData = restoreIndex(config, log)

		readCitCache().zip(readDoiCache()).flatMap: (citCache, doiCache) =>
			val (isFreshInit, baseSail) = StorageSail(config.rdfStorage)
			val citer = CitationProviderConfig.citationProvider(baseSail, citCache, doiCache, config)
			val derivedMetadata = DerivedMetadataService(citer)

			val indexFactories =
				if isFreshInit || config.rdfStorage.disableCpIndex then None
				else Some(IndexHandler(system.scheduler) -> GeoIndexProvider(using ExecutionContext.global))

			val sail = CpNotifyingSail(baseSail, indexFactories, citer, derivedMetadata)
			val repo = new SailRepository(sail)
			repo.init()

			val logManager = RdfLogManager(config.rdfLog, config.instanceServers, repo.getValueFactory)

			//NativeStore crashes under unrestrained parallel write conditions, LMDB does when re-built from the RDF logs
			val singleThreadExe = if config.rdfStorage.lmdb.isEmpty || isFreshInit then
				Some(ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor()))
			else None
			val writeExe = singleThreadExe.getOrElse(ExecutionContext.global)

			val written = Future(logManager.restore(repo, isFreshInit))(using writeExe)
				.flatMap(_ => ingestSchemaOntologies(repo, config, log)(using writeExe))
				.andThen(_ => singleThreadExe.foreach(_.shutdown()))

			written.map: _ =>
				val sparqlServer = new Rdf4jSparqlServer(repo, config.sparql)
				new RdfStore(repo, citer, derivedMetadata, sparqlServer, isFreshInit, sail, indexData, logManager)
	end start

	private def restoreIndex(config: CpmetaConfig, log: akka.event.LoggingAdapter)(using ExecutionContext): Future[Option[IndexData]] =
		val conf = config.rdfStorage
		val recreateIndex: Boolean = conf.recreateAtStartup || conf.recreateCpIndexAtStartup
		if recreateIndex then IndexHandler.dropStorage()
		if recreateIndex || conf.disableCpIndex then Future.successful(None)
		else
			log.info("Trying to restore SPARQL magic index...")
			val indexDataFut = IndexHandler.restore()
			indexDataFut.foreach: idx =>
				log.info(s"SPARQL magic index restored successfully (${idx.objs.length} objects)")
				IndexHandler.dropStorage()
			indexDataFut.map(Option(_)).recover:
				case err =>
					log.warning(s"Failed to restore SPARQL index (${err.getMessage})")
					None

	private def ingestSchemaOntologies(
		repo: SailRepository, config: CpmetaConfig, log: akka.event.LoggingAdapter
	)(using ExecutionContext): Future[Unit] =
		given valueFactory: ValueFactory = repo.getValueFactory
		given BnodeStabilizers = new BnodeStabilizers
		val schemaOntologies = for
			(id, conf) <- config.instanceServers.specific.toSeq
			ingestion <- conf.ingestion if ingestion.mode != IngestionMode.OFF
			owlResource <- Ingestion.schemaOntologyResources.get(ingestion.ingesterId)
		yield (id, conf, owlResource)

		Future.sequence(schemaOntologies.map: (id, conf, owlResource) =>
			val writeContext = conf.writeContext.toRdf
			val readContexts = conf.readContexts.fold(Seq(writeContext))(_.map(_.toRdf))
			val target = new Rdf4jInstanceServer(repo, readContexts, writeContext)
			Ingestion.ingest(target, new RdfXmlFileIngester(owlResource), valueFactory).andThen:
				case Success(_) => log.info("ingestion done for " + id)
				case Failure(err) => log.error(err, s"error while ingesting for $id")
		).map(_ => ())

end RdfStore
