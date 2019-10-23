package se.lu.nateko.cp.meta.services.sparql.magic

import java.time.Instant
import java.util.ArrayList
import java.util.concurrent.ArrayBlockingQueue

import scala.collection.JavaConverters._
import scala.collection.mutable.AnyRefMap
import scala.collection.mutable.ArrayBuffer

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import org.eclipse.rdf4j.sail.Sail

import org.roaringbitmap.buffer.MutableRoaringBitmap
import org.roaringbitmap.buffer.BufferFastAggregation
import org.roaringbitmap.buffer.ImmutableRoaringBitmap

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch
import se.lu.nateko.cp.meta.utils.async.ReadWriteLocking
import se.lu.nateko.cp.meta.utils.rdf4j._
import DataObjectFetch._
import se.lu.nateko.cp.meta.services.sparql.index._
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap.FilterRequest


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
	def sizeInBytes: Option[Long]
	def dataStartTime: Option[Literal]
	def dataEndTime: Option[Literal]
	def submissionStartTime: Option[Literal]
	def submissionEndTime: Option[Literal]
}

//TODO Make the index closeable (then it can permanently hold a single connection to the Sail)
class CpIndex(sail: Sail, nObjects: Int = 10000) extends ReadWriteLocking{
	import CpIndex._

	implicit val factory = sail.getValueFactory
	private val vocab = new CpmetaVocab(factory)
	private val idLookup = new AnyRefMap[Sha256Sum, Int](nObjects)
	private val objs = new ArrayBuffer[ObjEntry](nObjects)
	private val deprecated: MutableRoaringBitmap = emptyBitmap
	private val categMaps = new AnyRefMap[CategProp, AnyRefMap[_, MutableRoaringBitmap]]
	private val contMap = new AnyRefMap[ContProp, HierarchicalBitmap[_]]
	private val stats = new AnyRefMap[StatKey, MutableRoaringBitmap]

	def size: Int = objs.length

	private def categMap(prop: CategProp): AnyRefMap[prop.ValueType, MutableRoaringBitmap] = categMaps
		.getOrElseUpdate(prop, new AnyRefMap[prop.ValueType, MutableRoaringBitmap])
		.asInstanceOf[AnyRefMap[prop.ValueType, MutableRoaringBitmap]]

	private def bitmap(prop: ContProp): HierarchicalBitmap[prop.ValueType] = contMap.getOrElseUpdate(prop, prop match {
		/** Important to maintain type consistency between props and HierarchicalBitmaps here*/
		case FileName => StringHierarchicalBitmap{idx =>
			val uri = objs(idx).uri
			sail.accessEagerly{conn => //this is to avoid storing all the file names in memory
				val iter = conn.getStatements(uri, vocab.hasName, null, false)
				val res = if(iter.hasNext()) iter.next().getObject().stringValue() else ""
				iter.close()
				res
			}
		}
		case FileSize =>        FileSizeHierarchicalBitmap(idx => objs(idx).size)
		case DataStart =>       DatetimeHierarchicalBitmap(idx => objs(idx).dataStart)
		case DataEnd =>         DatetimeHierarchicalBitmap(idx => objs(idx).dataEnd)
		case SubmissionStart => DatetimeHierarchicalBitmap(idx => objs(idx).submissionStart)
		case SubmissionEnd =>   DatetimeHierarchicalBitmap(idx => objs(idx).submissionEnd)
	}).asInstanceOf[HierarchicalBitmap[prop.ValueType]]

	private val q = new ArrayBlockingQueue[RdfUpdate](UpdateQueueSize)

	//Mass-import of the specification info
	private val specRequiresStation: AnyRefMap[IRI, Boolean] = getStationRequirementsPerSpec(sail, vocab)

	//Mass-import of the statistics data
	sail.access[Statement](_.getStatements(null, null, null, false)).foreach(s => put(RdfUpdate(s, true)))
	flush()
	contMap.valuesIterator.foreach(_.optimizeAndTrim())


	def fetch(req: DataObjectFetch): Iterator[ObjInfo] = readLocked{
		//val start = System.currentTimeMillis

		def or(bms: Seq[MutableRoaringBitmap]): Option[MutableRoaringBitmap] =
			if(bms.isEmpty) None else Some(BufferFastAggregation.or(bms: _*))

		val categVarFilters = req.selections.flatMap{sel =>
			val perValue = categMap(sel.category)
			or(sel.values.map(v => perValue.getOrElse(v, emptyBitmap)))
		}

		val filterOpt: Option[ImmutableRoaringBitmap] = filterConjunction(
			categVarFilters ++ continuousPropFilters(req.filtering),
			req.filtering.filterDeprecated
		)

		val idxIter: Iterator[Int] = req.sort match{
			case None =>
				filterOpt.fold{
					objs.indices.drop(req.offset).iterator
				}{
					_.iterator.asScala.drop(req.offset).map(_.intValue)
				}
			case Some(SortBy(prop, descending)) =>
				bitmap(prop).iterateSorted(filterOpt, req.offset, descending)
		}
		//println(s"Fetch from CpIndex complete in ${System.currentTimeMillis - start} ms")
		idxIter.map(objs.apply)
	}

	private def continuousPropFilters(filtering: Filtering): Seq[ImmutableRoaringBitmap] = {
		val continuousVarFilters = filtering.filters.map{f =>
			bitmap(f.property).filter(f.condition)
		}

		val presentPropertiesFilters = filtering.requiredProps
			.diff(filtering.filters.map(_.property))
			.map(bitmap(_).all)

		continuousVarFilters ++ presentPropertiesFilters
	}

	private def filterConjunction(bms: Seq[ImmutableRoaringBitmap], filterDeprecated: Boolean): Option[ImmutableRoaringBitmap] = {
		val filterThusFar: Option[ImmutableRoaringBitmap] =
			if(bms.isEmpty) None
			else Some(BufferFastAggregation.and(bms: _*))
		if(filterDeprecated) filterDeprecation(filterThusFar) else filterThusFar
	}

	private def filterDeprecation(in: Option[ImmutableRoaringBitmap]): Option[ImmutableRoaringBitmap] = in match{
		case None =>
			Some(ImmutableRoaringBitmap.flip(deprecated, 0, (objs.length - 1).toLong))
		case Some(beforeDeprecation) =>
			Some(ImmutableRoaringBitmap.andNot(beforeDeprecation, deprecated))
	}

	def statEntries(filtering: Filtering): Iterable[StatEntry] = readLocked{
		val filterOpt: Option[ImmutableRoaringBitmap] = filterConjunction(
			continuousPropFilters(filtering),
			filtering.filterDeprecated
		)
		stats.flatMap{
			case (key, bm) =>
				val count = filterOpt.fold(bm.getCardinality)(ImmutableRoaringBitmap.andCardinality(bm, _))
				if(count > 0) Some(StatEntry(key, count))
				else None
		}
	}

	def lookupObject(hash: Sha256Sum): Option[ObjInfo] = idLookup.get(hash).map(objs.apply)

	private def getObjEntry(hash: Sha256Sum): ObjEntry = idLookup.get(hash).fold{
			val oe = new ObjEntry(hash, objs.length, "")
			objs += oe
			idLookup += hash -> oe.idx
			oe
		}(objs.apply)

	def put(st: RdfUpdate): Unit = {
		q.put(st)
		if(q.remainingCapacity == 0) flush()
	}

	def flush(): Unit = if(!q.isEmpty) writeLocked{
		if(q.isEmpty) return
		val list = new ArrayList[RdfUpdate](UpdateQueueSize)
		q.drainTo(list)

		list.forEach{
			case RdfUpdate(Rdf4jStatement(subj, pred, obj), isAssertion) =>
				processUpdate(subj, pred, obj, isAssertion)
			case _ => ()
		}
		list.clear()
	}

	private def processUpdate(subj: IRI, pred: IRI, obj: Value, isAssertion: Boolean): Unit = {
		import vocab._
		import vocab.prov.{wasAssociatedWith, startedAtTime, endedAtTime}


		def targetUri = if(isAssertion && obj.isInstanceOf[IRI]) obj.asInstanceOf[IRI] else null

		def handleContinuousPropUpdate[T](prop: ContProp{ type ValueType = T}, key: T, idx: Int): Unit = {
			if(isAssertion) bitmap(prop).add(key, idx)
			else bitmap(prop).remove(key, idx)
		}

		def updateCategSet[T <: AnyRef](set: AnyRefMap[T, MutableRoaringBitmap], categ: T, idx: Int): Unit = {
			val bm = set.getOrElseUpdate(categ, emptyBitmap)
			if(isAssertion) bm.add(idx) else bm.remove(idx)
		}
	
		pred match{

			case `hasObjectSpec` => obj match{
				case spec: IRI =>
					modForDobj(subj){oe =>
						updateCategSet(categMap(Spec), spec, oe.idx)
						if(!specRequiresStation.getOrElse(spec, true)) updateCategSet(categMap(Station), None, oe.idx)
						if(isAssertion) {
							if(oe.spec != null) removeStat(oe)
							oe.spec = spec
							addStat(oe)
						} else if(spec === oe.spec) {
							removeStat(oe)
							oe.spec = null
						}
					}
			}

			case `hasName` => modForDobj(subj){oe =>
				handleContinuousPropUpdate(FileName, obj.stringValue, oe.idx)
			}

			case `wasAssociatedWith` => subj match{
				case CpVocab.Submission(hash) =>
					val oe = getObjEntry(hash)
					oe.submitter = targetUri
					if(isAssertion) addStat(oe) else removeStat(oe)
					obj match{ case subm: IRI => updateCategSet(categMap(Submitter), subm, oe.idx) }

				case CpVocab.Acquisition(hash) =>
					val oe = getObjEntry(hash)
					oe.station = targetUri
					if(isAssertion) addStat(oe) else removeStat(oe)
					obj match{ case stat: IRI => updateCategSet(categMap(Station), Some(stat), oe.idx) }
				case _ =>
			}

			case `hasStartTime` => ifDateTime(obj){ dt =>
				modForDobj(subj){oe =>
					oe.dataStart = dt
					handleContinuousPropUpdate(DataStart, dt, oe.idx)
				}
			}

			case `hasEndTime` => ifDateTime(obj){ dt =>
				modForDobj(subj){oe =>
					oe.dataEnd = dt
					handleContinuousPropUpdate(DataEnd, dt, oe.idx)
				}
			}

			case `startedAtTime` => ifDateTime(obj){ dt =>
				subj match{
					case CpVocab.Acquisition(hash) =>
						val oe = getObjEntry(hash)
						oe.dataStart = dt
						handleContinuousPropUpdate(DataStart, dt, oe.idx)
					case CpVocab.Submission(hash) =>
						val oe = getObjEntry(hash)
						oe.submissionStart = dt
						handleContinuousPropUpdate(SubmissionStart, dt, oe.idx)
					case _ =>
				}
			}

			case `endedAtTime` => ifDateTime(obj){ dt =>
				subj match{
					case CpVocab.Acquisition(hash) =>
						val oe = getObjEntry(hash)
						oe.dataEnd = dt
						handleContinuousPropUpdate(DataEnd, dt, oe.idx)
					case CpVocab.Submission(hash) =>
						val oe = getObjEntry(hash)
						oe.submissionEnd = dt
						handleContinuousPropUpdate(SubmissionEnd, dt, oe.idx)
					case _ =>
				}
			}

			case `isNextVersionOf` =>
				modForDobj(obj)(oe => {
					if(isAssertion) deprecated.add(oe.idx)
					else deprecated.remove(oe.idx)
				})

			case `hasSizeInBytes` => ifLong(obj){size =>
				modForDobj(subj){oe =>
					if(isAssertion) oe.size = size
					else if(oe.size == size) oe.size = -1
					if(size >= 0) handleContinuousPropUpdate(FileSize, size, oe.idx)
				}
			}

			case _ =>
		}

	}

	private def modForDobj(dobj: Value)(mod: ObjEntry => Unit): Unit = dobj match{
		case CpVocab.DataObject(hash, prefix) =>
			val entry = getObjEntry(hash)
			if(entry.prefix == "") entry.prefix = prefix.intern()
			mod(entry)

		case _ =>
	}

	private def keyForDobj(obj: ObjEntry): Option[StatKey] = if(
		obj.spec == null || obj.submitter == null ||
		(obj.station == null && specRequiresStation.getOrElse(obj.spec, true))
	) None else Some(
		StatKey(obj.spec, obj.submitter, Option(obj.station))
	)

	private def removeStat(obj: ObjEntry): Unit = for(
		key <- keyForDobj(obj);
		bm <- stats.get(key)
	) bm.remove(obj.idx)

	private def addStat(obj: ObjEntry): Unit = for(
		key <- keyForDobj(obj);
		bm = stats.getOrElseUpdate(key, emptyBitmap)
	) bm.add(obj.idx)

}

object CpIndex{
	val UpdateQueueSize = 1 << 13

	def emptyBitmap = MutableRoaringBitmap.bitmapOf()

	private class ObjEntry(val hash: Sha256Sum, val idx: Int, var prefix: String)(implicit factory: ValueFactory) extends ObjInfo{
		var spec: IRI = _
		var submitter: IRI = _
		var station: IRI = _
		var size: Long = -1
		var dataStart: Long = Long.MinValue
		var dataEnd: Long = Long.MinValue
		var submissionStart: Long = Long.MinValue
		var submissionEnd: Long = Long.MinValue

		private def dateTimeFromLong(dt: Long): Option[Literal] =
			if(dt == Long.MinValue) None
			else Some(factory.createLiteral(Instant.ofEpochMilli(dt)))

		def sizeInBytes: Option[Long] = if(size >= 0) Some(size) else None
		def dataStartTime: Option[Literal] = dateTimeFromLong(dataStart)
		def dataEndTime: Option[Literal] = dateTimeFromLong(dataEnd)
		def submissionStartTime: Option[Literal] = dateTimeFromLong(submissionStart)
		def submissionEndTime: Option[Literal] = dateTimeFromLong(submissionEnd)

		def uri: IRI = factory.createIRI(prefix + hash.base64Url)
	}

	private def ifDateTime(dt: Value)(mod: Long => Unit): Unit = dt match{
		case lit: Literal if lit.getDatatype === XMLSchema.DATETIME =>
			try{
				mod(Instant.parse(lit.stringValue).toEpochMilli)
			}catch{
				case _: Throwable => //ignoring wrong dateTimes
			}
	}

	private def ifLong(dt: Value)(mod: Long => Unit): Unit = dt match{
		case lit: Literal if lit.getDatatype === XMLSchema.LONG =>
			try{
				mod(lit.stringValue.toLong)
			}catch{
				case _: Throwable => //ignoring wrong longs
			}
	}

	private def objSpecRequiresStation(spec: IRI, dataLevel: Literal): Boolean =
		dataLevel.intValue < 3 && !CpVocab.isIngosArchive(spec)

	private def getStationRequirementsPerSpec(sail: Sail, vocab: CpmetaVocab): AnyRefMap[IRI, Boolean] = {
		val map = new AnyRefMap[IRI, Boolean]
		sail.access[Statement](
			_.getStatements(null, vocab.hasDataLevel, null, false)
		).foreach{
			case Rdf4jStatement(subj, _, obj: Literal) =>
				map.update(subj, objSpecRequiresStation(subj, obj))
			case _ =>
		}
		map
	}

}
