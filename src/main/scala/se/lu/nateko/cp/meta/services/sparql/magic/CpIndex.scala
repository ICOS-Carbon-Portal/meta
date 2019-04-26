package se.lu.nateko.cp.meta.services.sparql.magic

import java.time.Instant
import java.util.ArrayList
import java.util.concurrent.ArrayBlockingQueue

import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import org.eclipse.rdf4j.sail.Sail

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.async.ReadWriteLocking
import se.lu.nateko.cp.meta.utils.rdf4j._


case class StatKey(spec: IRI, submitter: IRI, station: Option[IRI])
case class StatEntry(key: StatKey, count: Int)

trait ObjSpecific{
	def hash: Sha256Sum
	def uri: IRI
}

trait ObjInfo extends ObjSpecific{
	def spec: IRI
	def submitter: IRI
	def station: IRI
	def hasSubmission: Boolean
	def hasAcquisition: Boolean
	def dataStartTime: Option[Literal]
	def dataEndTime: Option[Literal]
	def isComplete: Boolean
	def isDeprecated: Boolean
	def getKeyOrElse(default: StatKey): StatKey
}

object CpIndex{
	val UpdateQueueSize = 1024
}

class CpIndex(sail: Sail) extends ReadWriteLocking{
	import CpIndex._

	private val factory = sail.getValueFactory
	private val vocab = new CpmetaVocab(factory)
	private val stats = new HashMap[Sha256Sum, ObjEntry]
	private val perSpec = new HashMap[IRI, HashSet[ObjEntry]]
	def objInfo: scala.collection.Map[Sha256Sum, ObjInfo] = stats

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

	def statEntries: Iterable[StatEntry] = readLocked{
		val nullKey = StatKey(null, null, None)
		stats.values
			.groupBy(_.getKeyOrElse(nullKey))
			.collect{
				case (key, vals) if key.ne(nullKey) => StatEntry(key, vals.size)
			}
			.toIndexedSeq
	}

	def getObjsForSpec(spec: IRI): Iterator[ObjInfo] = perSpec.get(spec).iterator.flatten

	private def objSpecRequiresStation(spec: IRI, dataLevel: Literal): Boolean =
		dataLevel.intValue < 3 && !CpVocab.isIngosArchive(spec)

	private def getObjEntry(hash: Sha256Sum): ObjEntry = stats.getOrElseUpdate(hash, new ObjEntry(hash, ""))
	private def getObjSetForSpec(spec: IRI): HashSet[ObjEntry] = perSpec.getOrElseUpdate(spec, HashSet.empty)

	private def modForDobj(dobj: Value)(mod: ObjEntry => Unit): Unit = dobj match{
		case CpVocab.DataObject(hash, prefix) =>
			val entry = getObjEntry(hash)
			if(entry.prefix == "") entry.prefix = prefix.intern()
			mod(entry)

		case _ =>
	}

	private def ifDateTime(dt: Value)(mod: Long => Unit): Unit = dt match{
		case lit: Literal if lit.getDatatype === XMLSchema.DATETIME =>
			try{
				mod(Instant.parse(lit.stringValue).getEpochSecond)
			}catch{
				case _: Throwable => //ignoring wrong dateTimes
			}
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
		import vocab.prov.{wasAssociatedWith, startedAtTime, endedAtTime}

		list.iterator.asScala.foreach{
			case RdfUpdate(Rdf4jStatement(subj, pred, obj), isAssertion) => {
				def targetUri = if(isAssertion && obj.isInstanceOf[IRI]) obj.asInstanceOf[IRI] else null

				pred match{

					case `hasObjectSpec` => obj match{
						case spec: IRI =>
							modForDobj(subj){oinfo =>
								val set = getObjSetForSpec(spec)
								if(isAssertion) {
									if(oinfo.spec != null && oinfo.spec != spec){//obj spec is changing
										getObjSetForSpec(oinfo.spec).remove(oinfo)
									}
									oinfo.spec = spec
									set.add(oinfo)
								} else if(spec === oinfo.spec) {
									oinfo.spec = null
									set.remove(oinfo)
								}
							}
					}

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

					case `hasStartTime` => ifDateTime(obj){ dt =>
						modForDobj(subj)(_.dataStart = dt)
					}

					case `hasEndTime` => ifDateTime(obj){ dt =>
						modForDobj(subj)(_.dataEnd = dt)
					}

					case `startedAtTime` => ifDateTime(obj){ dt =>
						subj match{
							case CpVocab.Acquisition(hash) =>
								getObjEntry(hash).dataStart = dt
							case _ =>
						}
					}

					case `endedAtTime` => ifDateTime(obj){ dt =>
						subj match{
							case CpVocab.Acquisition(hash) =>
								getObjEntry(hash).dataEnd = dt
							case _ =>
						}
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

	private class ObjEntry(val hash: Sha256Sum, var prefix: String) extends ObjInfo{
		var spec: IRI = _
		var submitter: IRI = _
		var station: IRI = _
		var hasSubmission: Boolean = false
		var hasAcquisition: Boolean = false
		var dataStart: Long = Long.MinValue
		var dataEnd: Long = Long.MinValue
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

		private def dataTime(dt: Long): Option[Literal] =
			if((!specRequiresStation.getOrElse(spec, true) || hasAcquisition) && dt != Long.MinValue)
				Some(factory.createLiteral(Instant.ofEpochSecond(dt)))
			else
				None

		def dataStartTime: Option[Literal] = dataTime(dataStart)
		def dataEndTime: Option[Literal] = dataTime(dataEnd)

		def uri: IRI = factory.createIRI(prefix + hash.base64Url)
	}

}
