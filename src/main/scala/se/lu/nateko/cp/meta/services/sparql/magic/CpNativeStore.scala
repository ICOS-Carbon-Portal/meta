package se.lu.nateko.cp.meta.services.sparql.magic

import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.nio.file.Files

import scala.compiletime.uninitialized

import akka.event.LoggingAdapter

import org.eclipse.rdf4j.sail.Sail

import se.lu.nateko.cp.meta.RdfStorageConfig
import se.lu.nateko.cp.meta.services.citation.*

import org.eclipse.rdf4j.sail.helpers.SailWrapper
import org.eclipse.rdf4j.sail.SailConnection
import scala.concurrent.Future
import akka.Done

class CpNativeStore(
	conf: RdfStorageConfig,
	indexInit: Sail => IndexProvider,
	citationFactory: CitationProviderFactory,
	log: LoggingAdapter
) extends SailWrapper{

	private var indexh: IndexProvider = uninitialized
	private var citer: CitationProvider = uninitialized
	private val storageDir = Paths.get(conf.path)

	val isFreshInit: Boolean = initStorageFolder() || conf.recreateAtStartup
	private val indices = if(isFreshInit) "" else conf.indices

	private val nativeSail = new CpInnerNativeStore(storageDir, indices, isFreshInit, () => indexh, () => citer)

	setBaseSail(nativeSail)

	def makeReadonly(errorMessage: String): Future[Done] = {
		nativeSail.makeReadonly(errorMessage)
		if(indexh == null) Future.successful(Done)
		else IndexHandler.store(indexh.index)
	}

	def getCitationClient: CitationClient = citer.doiCiter

	private val originalSail: Sail = new SailWrapper(nativeSail){
		override def getConnection(): SailConnection = nativeSail.getSpecificConnection(false)
	}

	override def init(): Unit = {
		if(isFreshInit) log.warning(
			"ATTENTION: THIS IS A FRESH INIT OF META SERVICE. RESTART ON COMPLETION WITH cpmeta.rdfStorage.recreateAtStartup = false"
		)
		log.info("Initializing triple store...")
		nativeSail.setForceSync(!isFreshInit)
		nativeSail.init()
		if(isFreshInit || conf.disableCpIndex){
			log.info("Triple store initialized, using a dummy as Carbon Portal index")
			indexh = new DummyIndexProvider
		} else {
			log.info("Triple store initialized, initializing Carbon Portal index...")
			indexh = indexInit(originalSail)
			log.info(s"Carbon Portal index initialized with info on ${indexh.index.size} data objects")
		}
		citer = citationFactory.getProvider(originalSail)
		log.info("Initialized citation provider")
	}

	override def getConnection(): SailConnection = nativeSail.getSpecificConnection(!isFreshInit)

	private def initStorageFolder(): Boolean = {

		val didNotExist = !Files.exists(storageDir)

		def storageFiles = Files.walk(storageDir).filter(Files.isRegularFile(_))

		if(didNotExist)
			Files.createDirectories(storageDir)
		else if(conf.recreateAtStartup){
			log.info("Purging the current native RDF storage")
			storageFiles.forEach(Files.delete)
		}
		didNotExist || !storageFiles.findAny.isPresent
	}
}
