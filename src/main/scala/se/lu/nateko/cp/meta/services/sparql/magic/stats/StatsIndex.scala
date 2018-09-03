package se.lu.nateko.cp.meta.services.sparql.magic.stats

import java.util.ArrayList
import java.util.concurrent.ArrayBlockingQueue

import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.sail.Sail

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.async.ReadWriteLocking
import se.lu.nateko.cp.meta.utils.rdf4j._


case class StatKey(spec: IRI, submitter: IRI, station: Option[IRI])
case class StatEntry(key: StatKey, count: Int)

object StatsIndex{
	val UpdateQueueSize = 1024
}

class StatsIndex(sail: Sail) extends ReadWriteLocking{
	import StatsIndex._

	private val vocab = new CpmetaVocab(sail.getValueFactory)
	private val stats = new HashMap[Sha256Sum, ObjEntry]
	private val q = new ArrayBlockingQueue[RdfUpdate](UpdateQueueSize)

	//Mass-import of the specification info
	private val specRequiresStation: HashMap[IRI, Boolean] = {
		val map = HashMap.empty[IRI, Boolean]
		sail.access[Statement](
			_.getStatements(null, vocab.hasDataLevel, null, false)
		).foreach{
			case Rdf4jStatement(subj, _, obj: Literal) =>
				map.update(subj, objSpecRequiresStation(subj, obj))
			case _ =>
		}
		map
	}

	//Mass-import of the statistics data
	sail.access[Statement](_.getStatements(null, null, null, false)).foreach(s => put(RdfUpdate(s, true)))

	def entries: Iterable[StatEntry] = readLocked{
		val nullKey = StatKey(null, null, None)
		stats.values
			.groupBy(_.getKeyOrElse(nullKey))
			.collect{
				case (key, vals) if key.ne(nullKey) => StatEntry(key, vals.size)
			}
			.toIndexedSeq
	}

	private def objSpecRequiresStation(spec: IRI, dataLevel: Literal): Boolean =
		dataLevel.intValue < 3 && !CpVocab.isIngosArchive(spec)

	private def getObjEntry(hash: Sha256Sum): ObjEntry = stats.getOrElseUpdate(hash, new ObjEntry)

	private def modForDobj(dobj: Value)(mod: ObjEntry => Unit): Unit = dobj match{
		case CpVocab.DataObject(hash) => mod(getObjEntry(hash))
		case _ =>
	}

	def put(st: RdfUpdate): Unit = {
		q.put(st)
		if(q.remainingCapacity == 0) flush()
	}

	def flush(): Unit = if(!q.isEmpty) writeLocked{
		if(q.isEmpty) return

		val list = new ArrayList[RdfUpdate](UpdateQueueSize)
		q.drainTo(list)
		import vocab._
		import vocab.prov.wasAssociatedWith

		list.iterator.asScala.foreach{
			case RdfUpdate(Rdf4jStatement(subj, pred, obj), isAssertion) => {
				def targetUri = if(isAssertion && obj.isInstanceOf[IRI]) obj.asInstanceOf[IRI] else null

				pred match{

					case `hasObjectSpec` =>
						modForDobj(subj)(_.spec = targetUri)

					case `wasSubmittedBy` =>
						modForDobj(subj)(_.hasSubmission = isAssertion)

					case `wasAcquiredBy` =>
						modForDobj(subj)(_.hasAcquisition = isAssertion)

					case `wasAssociatedWith` => subj match{
						case CpVocab.Submission(hash) =>
							getObjEntry(hash).submitter = targetUri
						case CpVocab.Acquisition(hash) =>
							getObjEntry(hash).station = targetUri
						case _ =>
					}

					case `isNextVersionOf` =>
						modForDobj(obj)(_.isDeprecated = isAssertion)

					case `hasSizeInBytes` =>
						modForDobj(subj)(_.isComplete = isAssertion)

					case `hasDataLevel` => if(isAssertion) obj match{
						case lit: Literal =>
							specRequiresStation.update(subj, objSpecRequiresStation(subj, lit))
						case _ =>
					}

					case _ =>
				}
			}

			case _ =>
		}
		list.clear()
	}

	private class ObjEntry{
		var spec: IRI = _
		var submitter: IRI = _
		var station: IRI = _
		var hasSubmission: Boolean = false
		var hasAcquisition: Boolean = false
		var isComplete: Boolean = false
		var isDeprecated: Boolean = false

		def getKeyOrElse(default: StatKey): StatKey =
			if(
				spec != null && submitter != null && hasSubmission &&
				isComplete && !isDeprecated &&
				(!specRequiresStation.getOrElse(spec, true) || hasAcquisition && station != null)
			) StatKey(
				spec,
				submitter,
				if(station != null && hasAcquisition) Some(station) else None
			)
			else default
	}

}
