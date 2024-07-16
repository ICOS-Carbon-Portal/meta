package se.lu.nateko.cp.meta.services.sparql.magic

import akka.Done
import akka.event.LoggingAdapter
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailConnection
import org.eclipse.rdf4j.sail.SailConnectionListener
import org.eclipse.rdf4j.sail.helpers.SailWrapper
import se.lu.nateko.cp.meta.RdfStorageConfig
import se.lu.nateko.cp.meta.services.citation.*
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.IndexData
import se.lu.nateko.cp.meta.utils.async.ok

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Paths
import scala.compiletime.uninitialized
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

class CpNativeStore(
	conf: RdfStorageConfig,
	listenerFactory: IndexHandler,
	geoFactory: GeoIndexProvider,
	citationFactory: CitationProviderFactory,
	log: LoggingAdapter
) extends SailWrapper{

	private var cpIndex: Option[CpIndex] = None
	private val storageDir = Paths.get(conf.path)

	val isFreshInit: Boolean = initStorageFolder() || conf.recreateAtStartup
	private val indices = if(isFreshInit) "" else conf.indices
	private val disableCpIndex = isFreshInit || conf.disableCpIndex

	private val nativeSail = new CpInnerNativeStore(storageDir, indices)

	setBaseSail(nativeSail)

	def makeReadonly(errorMessage: String)(using ExecutionContext): Future[String] =
		if nativeSail.isReadonly then
			Future.successful("Triple store already in read-only mode")
		else
			nativeSail.makeReadonly(errorMessage)
			val indexDump = cpIndex.fold(ok){idx =>
				idx.flush()
				IndexHandler.store(idx)
			}
			val citClient = getCitationClient
			val citationsDump = CitationClient.writeCitCache(citClient)
			val doiMetaDump = CitationClient.writeDoiCache(citClient)
			Future.sequence(Seq(indexDump, citationsDump, doiMetaDump)).map(_ =>
				"Switched the triple store to read-only mode. SPARQL index and citations cache dumped to disk"
			).andThen{
				case Success(msg) => log.info(msg)
				case Failure(err) => log.error(err, "Fail while dumping SPARQL index or citations cache to disk")
			}

	def getCitationProvider: CitationProvider = nativeSail.enricher.citer
	def getCitationClient: CitationClient = getCitationProvider.doiCiter

	override def init(): Unit = {
		if(isFreshInit) log.warning(
			"ATTENTION: THIS IS A FRESH INIT OF META SERVICE. RESTART ON COMPLETION WITH cpmeta.rdfStorage.recreateAtStartup = false"
		)
		log.info("Initializing triple store...")
		val forceSync = !isFreshInit
		log.info(s"Setting force-sync to '$forceSync'")
		nativeSail.setForceSync(forceSync)
		nativeSail.init()
		nativeSail.enricher = StatementsEnricher(citationFactory(nativeSail))
		setupQueryEvaluation(magicIdxOpt = None)
		log.info("Triple store initialized")
	}

	private def setupQueryEvaluation(magicIdxOpt: Option[CpIndex]): Unit =
		val magicIdx = magicIdxOpt.getOrElse:
			new CpIndex(nativeSail, Future.never, IndexData(0)())(log)
		nativeSail.setEvaluationStrategyFactory(
			new CpEvaluationStrategyFactory(nativeSail.getFederatedServiceResolver(), magicIdx, nativeSail.enricher, magicIdxOpt.isDefined)
		)

	def initSparqlMagicIndex(idxData: Option[IndexData]): Future[Done] =
		if disableCpIndex then
			log.info("Magic SPARQL index is disabled")
			ok
		else
			if(idxData.isEmpty) log.info("Initializing Carbon Portal index...")
			val geoPromise = Promise[(GeoIndex, GeoEventProducer)]()
			val geoFut = geoPromise.future.map(_._1)(ExecutionContext.parasitic)
			val idx = idxData.fold(new CpIndex(nativeSail, geoFut)(log))(idx => new CpIndex(nativeSail, geoFut, idx)(log))
			idx.flush()
			nativeSail.listener = listenerFactory.getListener(nativeSail, getCitationProvider.metaVocab, idx, geoPromise.future)
			geoPromise.completeWith(geoFactory.index(nativeSail, idx, getCitationProvider.metaReader))
			if(idxData.isEmpty) log.info(s"Carbon Portal index initialized with info on ${idx.size} data objects")
			cpIndex = Some(idx)
			setupQueryEvaluation(cpIndex)
			geoFut.map(_ => Done)(using ExecutionContext.parasitic)


	override def getConnection(): SailConnection =
		if(isFreshInit) nativeSail.getConnection()
		else nativeSail.getCpConnection()

	private def initStorageFolder(): Boolean = {

		val didNotExist = !Files.exists(storageDir)

		def storageFiles = Files.walk(storageDir, FileVisitOption.FOLLOW_LINKS).filter(Files.isRegularFile(_))

		if(didNotExist)
			Files.createDirectories(storageDir)
		else if(conf.recreateAtStartup){
			log.info("Purging the current native RDF storage")
			storageFiles.forEach(Files.delete)
		}
		didNotExist || !storageFiles.findAny.isPresent
	}
}
