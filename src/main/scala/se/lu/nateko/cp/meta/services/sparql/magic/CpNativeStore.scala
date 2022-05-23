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
import se.lu.nateko.cp.meta.utils.async.ok

import org.eclipse.rdf4j.sail.helpers.SailWrapper
import org.eclipse.rdf4j.sail.SailConnection
import scala.concurrent.Future
import akka.Done
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.IndexData
import org.eclipse.rdf4j.sail.SailConnectionListener

class CpNativeStore(
	conf: RdfStorageConfig,
	listenerFactory: CpIndex => SailConnectionListener,
	citationFactory: CitationProviderFactory,
	log: LoggingAdapter
) extends SailWrapper{

	private var cpIndex: Option[CpIndex] = None
	private val storageDir = Paths.get(conf.path)

	val isFreshInit: Boolean = initStorageFolder() || conf.recreateAtStartup
	private val indices = if(isFreshInit) "" else conf.indices
	private val disableCpIndex = isFreshInit || conf.disableCpIndex

	private val nativeSail = new CpInnerNativeStore(storageDir, indices, disableCpIndex)

	setBaseSail(nativeSail)

	def makeReadonly(errorMessage: String): Future[Done] = {
		nativeSail.makeReadonly(errorMessage)
		cpIndex.fold(ok)(IndexHandler.store)
	}

	def getCitationClient: CitationClient = nativeSail.citer.doiCiter

	override def init(): Unit = {
		if(isFreshInit) log.warning(
			"ATTENTION: THIS IS A FRESH INIT OF META SERVICE. RESTART ON COMPLETION WITH cpmeta.rdfStorage.recreateAtStartup = false"
		)
		log.info("Initializing triple store...")
		nativeSail.setForceSync(!isFreshInit)
		nativeSail.init()
		log.info("Triple store initialized")
		nativeSail.citer = citationFactory.getProvider(nativeSail)
		log.info("Initialized citation provider")
	}

	def initSparqlMagicIndex(idxData: Option[IndexData]): Unit = {
		cpIndex = if(disableCpIndex){
			log.info("Using a dummy as Carbon Portal index")
			None
		} else {
			if(idxData.isEmpty) log.info("Initializing Carbon Portal index...")
			val idx = idxData.fold(new CpIndex(nativeSail)(log))(idx => new CpIndex(nativeSail, idx)(log))
			idx.flush()
			nativeSail.listener = listenerFactory(idx)
			if(idxData.isEmpty) log.info(s"Carbon Portal index initialized with info on ${idx.size} data objects")
			Some(idx)
		}
		cpIndex.foreach{idx =>
			nativeSail.setEvaluationStrategyFactory(
				new CpEvaluationStrategyFactory(nativeSail.getFederatedServiceResolver(), idx)
			)
		}
	}

	override def getConnection(): SailConnection =
		if(isFreshInit) nativeSail.getConnection()
		else nativeSail.getCpConnection()

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
