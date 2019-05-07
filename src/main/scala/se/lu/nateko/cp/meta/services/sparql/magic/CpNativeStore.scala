package se.lu.nateko.cp.meta.services.sparql.magic

import java.io.File
import java.io.IOException
import akka.event.LoggingAdapter
import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunctionRegistry
import org.eclipse.rdf4j.sail.nativerdf.NativeStore
import org.eclipse.rdf4j.sail.nativerdf.NativeStoreConnection
import org.eclipse.rdf4j.sail.NotifyingSailConnection
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailException
import se.lu.nateko.cp.meta.services.CitationProviderFactory
import se.lu.nateko.cp.meta.services.CitationProvider
import se.lu.nateko.cp.meta.api.CitationClient
import se.lu.nateko.cp.meta.services.sparql.magic.stats.StatsTupleFunction
import org.eclipse.rdf4j.sail.helpers.SailWrapper
import org.eclipse.rdf4j.sail.SailConnection
import java.util.concurrent.locks.ReentrantReadWriteLock

class CpNativeStore(
	storageFolder: File,
	init: Sail => IndexHandler,
	citationFactory: CitationProviderFactory,
	log: LoggingAdapter
) extends SailWrapper{ cpsail =>

	private var indexh: IndexHandler = _
	private var citer: CitationProvider = _

	private var useCpConnection: Boolean = false

	private val nativeSail: NativeStore = new NativeStore(storageFolder, CpNativeStore.indices){
		override def getConnectionInternal(): NotifyingSailConnection =
			if(!useCpConnection)
				super.getConnectionInternal()
			else
				try {
					val conn = new CpNativeStoreConnection(nativeSail, citer)
					conn.addConnectionListener(indexh)
					conn
				}
				catch {
					case e: IOException =>
						throw new SailException(e)
				}
	}
	setBaseSail(nativeSail)

	private val connLock = new ReentrantReadWriteLock()
	private def getSpecificConnection(cpSpecific: Boolean): SailConnection = {
		val lock = connLock.writeLock()
		lock.lock()
		try{
			useCpConnection = cpSpecific
			nativeSail.getConnection()
		}finally{
			lock.unlock()
		}
	}

	nativeSail.setEvaluationStrategyFactory{
		val tupleFunctionReg = new TupleFunctionRegistry()
		val indexThunk = () => indexh.index
		tupleFunctionReg.add(new StatsTupleFunction(indexThunk))
		new CpEvaluationStrategyFactory(tupleFunctionReg, nativeSail.getFederatedServiceResolver, indexThunk)
	}

	def getCitationClient: CitationClient = citer.dataCiter
	def setForceSync(forceSync: Boolean): Unit = nativeSail.setForceSync(forceSync)

	private val originalSail = new SailWrapper(nativeSail){
		override	def getConnection(): SailConnection = getSpecificConnection(false)
	}

	override def initialize(): Unit = {
		log.info("Initializing triple store...")
		nativeSail.initialize()
		log.info("Triple store initialized, initializing Carbon Portal index...")
		indexh = init(originalSail)
		log.info(s"Carbon Portal index initialized with info on ${indexh.index.objInfo.size} data objects")
		citer = citationFactory.getProvider(originalSail)
		log.info("Initialized citation provider")
	}

	override	def getConnection(): SailConnection = getSpecificConnection(true)
}

object CpNativeStore{
//	val indices = "spoc,posc,opsc,cspo,csop,cpso,cpos,cosp,cops"
//	val indices = "spoc".permutations.mkString(",") //all the possible indices
	val indices = "spoc,posc,ospc,cspo,cpos,cosp"
}
