package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import se.lu.nateko.cp.meta.api.SparqlQuery

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import org.eclipse.rdf4j.repository.sail.SailRepository
import se.lu.nateko.cp.cpauth.core.ConfigLoader.appConfig
import se.lu.nateko.cp.meta.{ConfigLoader, CpmetaConfig, IngestionMode}
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

object Main extends App:

	private val config: CpmetaConfig = ConfigLoader.default
	private val sparqlConfig = config.sparql
	private val host = config.rdfStore.httpBindInterface
	private val port = config.rdfStore.port

	private given system: ActorSystem = ActorSystem("cpmeta-rdf-store", appConfig)
	private given ExecutionContext = system.dispatcher
	private given EnvriConfigs = config.core.envriConfigs

	private val startup = {
		val (isFreshInit, baseSail) = StorageSail(config.rdfStorage)
		val citer = CitationProvider(baseSail, config)
		val derivedMetadata = DerivedMetadataService(citer)
		val indexFactories =
			if isFreshInit || config.rdfStorage.disableCpIndex then None
			else Some(IndexHandler(system.scheduler) -> GeoIndexProvider(using ExecutionContext.global))
		val sail = CpNotifyingSail(baseSail, indexFactories, citer, derivedMetadata)
		val logManager = RdfLogManager(
			config.rdfLog,
			config.instanceServers,
			baseSail.getValueFactory
		)
		val repo = SailRepository(sail)
		repo.init()
		logManager.restore(repo, isFreshInit)
		val schemaOntologiesIngested = ingestSchemaOntologies(repo, config)
		for {
			_ <- schemaOntologiesIngested
			_ <- sail.initSparqlMagicIndex()
			_ = if isFreshInit then sail.makeReadonly(
				"Fresh RDF-log restoration is complete; restart rdfStore for normal indexed operation"
			)
			queryServer = Rdf4jSparqlServer(repo, sparqlConfig)
			given ToResponseMarshaller[SparqlQuery] = queryServer.marshaller
			binding <- Http().newServerAt(host, port).bind(Route(
				repo,
				sparqlConfig,
				derivedMetadata,
				message => Future.successful(sail.switchToReadonly(message))
			))
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

	private def ingestSchemaOntologies(
		repo: SailRepository, config: CpmetaConfig
	)(using ExecutionContext): Future[Unit] =
		given valueFactory: org.eclipse.rdf4j.model.ValueFactory = repo.getValueFactory
		given BnodeStabilizers = new BnodeStabilizers
		val schemaOntologies = for
			conf <- config.instanceServers.specific.values
			ingestion <- conf.ingestion if ingestion.mode != IngestionMode.OFF
			owlResource <- Ingestion.schemaOntologyResources.get(ingestion.ingesterId)
		yield conf.writeContext -> owlResource
		Future.sequence(schemaOntologies.map{ (writeContextUri, owlResource) =>
			val writeContext = writeContextUri.toRdf
			val target = new Rdf4jInstanceServer(repo, writeContext)
			Ingestion.ingest(target, new RdfXmlFileIngester(owlResource), valueFactory).andThen:
				case Success(_) => system.log.info("ingested schema ontology into {}", writeContext)
				case Failure(err) => system.log.error(err, "failed to ingest schema ontology into {}", writeContext)
		}).map(_ => ())
