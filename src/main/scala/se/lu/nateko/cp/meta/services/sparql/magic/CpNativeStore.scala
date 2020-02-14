package se.lu.nateko.cp.meta.services.sparql.magic

import java.io.File
import java.io.IOException
import akka.event.LoggingAdapter
import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunctionRegistry
import org.eclipse.rdf4j.sail.nativerdf.NativeStore
import org.eclipse.rdf4j.sail.nativerdf.CpNativeStoreConnection
import org.eclipse.rdf4j.sail.NotifyingSailConnection
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailException
import se.lu.nateko.cp.meta.services.CitationProviderFactory
import se.lu.nateko.cp.meta.services.CitationProvider
import se.lu.nateko.cp.meta.api.CitationClient
import se.lu.nateko.cp.meta.services.sparql.magic.stats.StatsTupleFunction
import se.lu.nateko.cp.meta.utils.async.ReadWriteLocking
import org.eclipse.rdf4j.sail.helpers.SailWrapper
import org.eclipse.rdf4j.sail.SailConnection
import akka.actor.ActorSystem

class CpNativeStore(
	storageFolder: File,
	indexInit: Sail => IndexHandler,
	citationFactory: CitationProviderFactory,
	log: LoggingAdapter
)(implicit system: ActorSystem) extends SailWrapper{ cpsail =>

	private[this] var indexh: IndexHandler = _
	private[this] var citer: CitationProvider = _

	private object nativeSail extends NativeStore(storageFolder, CpNativeStore.indices) with ReadWriteLocking{

		private[this] var useCpConnection: Boolean = true

		setEvaluationStrategyFactory{
			val tupleFunctionReg = new TupleFunctionRegistry()
			val indexThunk = () => indexh.index
			tupleFunctionReg.add(new StatsTupleFunction(indexThunk))
			new CpEvaluationStrategyFactory(tupleFunctionReg, getFederatedServiceResolver, indexThunk)
		}

		def getSpecificConnection(cpSpecific: Boolean): SailConnection = writeLocked{
			useCpConnection = cpSpecific
			val conn = getConnection()
			useCpConnection = true
			conn
		}

		override def getConnectionInternal(): NotifyingSailConnection =
			if(useCpConnection)
				try {
					val conn = new CpNativeStoreConnection(this, citer)
					conn.addConnectionListener(indexh)
					conn
				}
				catch {
					case e: IOException =>
						throw new SailException(e)
				}
			else
				super.getConnectionInternal()
	}

	setBaseSail(nativeSail)

	def getCitationClient: CitationClient = citer.dataCiter
	def setForceSync(forceSync: Boolean): Unit = nativeSail.setForceSync(forceSync)

	private val originalSail: Sail = new SailWrapper(nativeSail){
		override	def getConnection(): SailConnection = nativeSail.getSpecificConnection(false)
	}

	override def initialize(): Unit = {
		log.info("Initializing triple store...")
		nativeSail.initialize()
		log.info("Triple store initialized, initializing Carbon Portal index...")
		indexh = indexInit(originalSail)
		log.info(s"Carbon Portal index initialized with info on ${indexh.index.size} data objects")
		citer = citationFactory.getProvider(originalSail)
		log.info("Initialized citation provider")
	}

	override	def getConnection(): SailConnection = nativeSail.getSpecificConnection(true)
}

object CpNativeStore{
//	val indices = "spoc,posc,opsc,cspo,csop,cpso,cpos,cosp,cops"
//	val indices = "spoc".permutations.mkString(",") //all the possible indices
	val indices = "spoc,posc,ospc,cspo,cpos,cosp"
}
