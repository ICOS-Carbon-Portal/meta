package se.lu.nateko.cp.meta.services.sparql.magic.stats

import java.util.ArrayList
import java.util.concurrent.ArrayBlockingQueue

import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.sail.Sail

import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.async.ReadWriteLocking
import se.lu.nateko.cp.meta.utils.rdf4j._


case class StatKey(spec: IRI, submitter: IRI, station: Option[IRI])
case class StatEntry(key: StatKey, count: Int)

//TODO Handle incomplete uploads and deprecated data objects
class StatsIndex(sail: Sail) extends ReadWriteLocking{
	import StatsIndex._

	private val vocab = new CpmetaVocab(sail.getValueFactory)
	private val stats = new HashMap[StatKey, Int]
	private val addUpdater = new StatsUpdater(1)
	private val removeUpdater = new StatsUpdater(-1)

	private val specRequiresStation: Map[IRI, Boolean] = sail.access[Statement](
			_.getStatements(null, vocab.hasDataLevel, null, false)
		).collect{
			case Rdf4jStatement(subj, _, obj: Literal) =>
				subj -> (obj.integerValue.intValue < 3)
		}.toMap

	//Mass-import of the statistics data
	sail.access[Statement](_.getStatements(null, null, null, false)).foreach(add)

	def entries: Iterable[StatEntry] = readLocked{
		val entries = for((key, count) <- stats) yield StatEntry(key, count)
		entries.toIndexedSeq
	}

	def add(st: Statement): Unit = addUpdater.put(st)
	def remove(st: Statement): Unit = removeUpdater.put(st)

	def flush(): Unit = {
		addUpdater.flush()
		removeUpdater.flush()
	}

	private class StatsUpdater(increment: Int){

		private val stq = new ArrayBlockingQueue[Statement](StatementQueueSize)
		private val obj2spec = HashMap.empty[IRI, IRI]
		private val obj2submission = HashMap.empty[IRI, IRI]
		private val subm2submitter = HashMap.empty[IRI, IRI]
		private val obj2acq = HashMap.empty[IRI, IRI]
		private val acq2station = HashMap.empty[IRI, IRI]

		def put(st: Statement): Unit = {
			stq.put(st)
			if(stq.remainingCapacity == 0) flush()
		}

		def flush(): Unit = if(!stq.isEmpty) writeLocked{
			if(stq.isEmpty) return

			val list = new ArrayList[Statement](StatementQueueSize)
			stq.drainTo(list)
			import vocab._
			import vocab.prov.wasAssociatedWith

			list.iterator.asScala.foreach{
				case Rdf4jStatement(subj, pred, obj: IRI) => pred match{

					case `hasObjectSpec` => obj2spec += subj -> obj

					case `wasSubmittedBy` => obj2submission += subj -> obj

					case `wasAcquiredBy` => obj2acq += subj -> obj

					case `wasAssociatedWith` =>
						if(CpVocab.looksLikeSubmission(subj))
							subm2submitter += subj -> obj
						else if(CpVocab.looksLikeAcquisition(subj))
							acq2station += subj-> obj
					case _ =>
				}

				case _ =>
			}
			list.clear()
			for(
				(obj, spec) <- obj2spec;
				submission <- obj2submission.get(obj);
				submitter <- subm2submitter.get(submission);
				acqOpt = obj2acq.get(obj);
				stationOpt = acqOpt.flatMap(acq2station.get);
				stationRequired <- specRequiresStation.get(spec)
				if(stationOpt.isDefined || !stationRequired)
			){
				val key = StatKey(spec, submitter, stationOpt)
				val newCount: Int = stats.getOrElse(key, 0) + increment
				stats += key -> newCount
				obj2spec -= obj
				obj2submission -= obj
				subm2submitter -= submission
				obj2acq -= obj
				acqOpt.foreach(acq2station.remove)
			}
		}
	}
}

object StatsIndex{
	val StatementQueueSize = 1024
}
