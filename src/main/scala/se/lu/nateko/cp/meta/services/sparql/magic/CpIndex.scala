package se.lu.nateko.cp.meta.services.sparql.magic

import akka.event.LoggingAdapter
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.sail.Sail
import org.roaringbitmap.buffer.BufferFastAggregation
import org.roaringbitmap.buffer.ImmutableRoaringBitmap
import org.roaringbitmap.buffer.MutableRoaringBitmap
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.utils.async.ReadWriteLocking
import se.lu.nateko.cp.meta.utils.rdf4j.*


import java.io.Serializable
import java.time.Instant
import java.util.ArrayList
import java.util.concurrent.ArrayBlockingQueue
import scala.collection.mutable.AnyRefMap
import scala.concurrent.Future
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.Failure
import scala.util.Success

import CpIndex.*
import se.lu.nateko.cp.meta.services.sparql.magic.index.{IndexData, ObjEntry, StatEntry, emptyBitmap}

trait ObjSpecific{
	def hash: Sha256Sum
	def uri(factory: ValueFactory): IRI
}

trait ObjInfo extends ObjSpecific{
	def spec: IRI
	def submitter: IRI
	def station: IRI
	def site: IRI
	def fileName: Option[String]
	def sizeInBytes: Option[Long]
	def samplingHeightMeters: Option[Float]
	def dataStartTime: Option[Instant]
	def dataEndTime: Option[Instant]
	def submissionStartTime: Option[Instant]
	def submissionEndTime: Option[Instant]
}

class CpIndex(sail: Sail, geo: Future[GeoIndex], data: IndexData)(using log: LoggingAdapter) extends ReadWriteLocking:

	import data.*
	def this(sail: Sail, geo: Future[GeoIndex], nObjects: Int = 10000)(using log: LoggingAdapter) = {
		this(sail, geo, IndexData(nObjects)())
		//Mass-import of the statistics data
		var statementCount = 0
		sail.accessEagerly:
			TriplestoreConnection.getStatements(null, null, null)
			.foreach: s =>
				put(RdfUpdate(s, true))
				statementCount += 1
				if statementCount % 1000000 == 0 then
					log.info(s"SPARQL magic index received ${statementCount / 1000000} million RDF assertions by now...")
		flush()
		contMap.valuesIterator.foreach(_.optimizeAndTrim())
		stats.filterInPlace{case (_, bm) => !bm.isEmpty}
		log.info(s"SPARQL magic index initialized by $statementCount RDF assertions")
		reportDebugInfo()
	}
	private def reportDebugInfo(): Unit =
		log.debug(s"Amount of objects in 'initOk' is ${data.initOk.getCardinality}")
		val objsInStats = stats.valuesIterator.map(_.getCardinality).sum
		log.debug(s"Amount of objects in stats is $objsInStats")
		log.debug(s"Following fieldsites data object keys are present in the index:")
		stats.foreach{
			case (key, bm) =>
				if key.spec.toString.contains("fieldsites") then
					log.debug(s"Count ${bm.getCardinality} for key $key")
		}

	if stats.nonEmpty then
		log.info("CpIndex got initialized with non-empty index data to use")
		reportDebugInfo()

	given factory: ValueFactory = sail.getValueFactory
	val vocab = new CpmetaVocab(factory)

	private val q = new ArrayBlockingQueue[RdfUpdate](UpdateQueueSize)

	def size: Int = objs.length
	def serializableData: Serializable = data

	private def boolBitmap(prop: BoolProperty): MutableRoaringBitmap = boolMap.getOrElseUpdate(prop, emptyBitmap)

	private def categMap(prop: CategProp): AnyRefMap[prop.ValueType, MutableRoaringBitmap] = categMaps
		.getOrElseUpdate(prop, new AnyRefMap[prop.ValueType, MutableRoaringBitmap])
		.asInstanceOf[AnyRefMap[prop.ValueType, MutableRoaringBitmap]]

	def fetch(req: DataObjectFetch): Iterator[ObjInfo] = readLocked{
		//val start = System.currentTimeMillis

		val filter = filtering(req.filter).fold(initOk)(BufferFastAggregation.and(_, initOk))

		val idxIter: Iterator[Int] = req.sort match{
			case None =>
				filter.iterator.asScala.drop(req.offset).map(_.intValue)
			case Some(SortBy(prop, descending)) =>
				data.bitmap(prop).iterateSorted(Some(filter), req.offset, descending)
		}
		//println(s"Fetch from CpIndex complete in ${System.currentTimeMillis - start} ms")
		idxIter.map(objs.apply)
	}

	def filtering(filter: Filter): Option[ImmutableRoaringBitmap] = filter match{
		case And(filters) =>
			val geoFilts = filters.collect{case gf: GeoFilter => gf}

			if geoFilts.isEmpty then andFiltering(filters) else
				val nonGeoFilts = filters.filter:
					case gf: GeoFilter => false
					case _ => true
				val nonGeoBm = andFiltering(nonGeoFilts)
				val geoBms = geoFilts.flatMap(geoFiltering(_, nonGeoBm))
				if geoBms.isEmpty then nonGeoBm else and(geoBms ++ nonGeoBm)

		case Not(filter) => filtering(filter) match {
			case None => Some(emptyBitmap)
			case Some(bm) => Some(negate(bm))
		}

		case Exists(prop) => prop match{
			case cp: ContProp => Some(data.bitmap(cp).all)
			case cp: CategProp => cp match{
				case optUriProp: OptUriProperty => categMap(optUriProp).get(None) match{
					case None => None
					case Some(deprived) if deprived.isEmpty => None
					case Some(deprived) => Some(negate(deprived))
				}
				case _ => None
			}
			case boo: BoolProperty => Some(boolBitmap(boo))
			case _: GeoProp => None
		}

		case ContFilter(property, condition) =>
			Some(bitmap(property).filter(condition))

		case CategFilter(category, values) if category == DobjUri =>
			val objIndices: Seq[Int] = values
				.collect{case iri: IRI => iri}
				.collect{case CpVocab.DataObject(hash, _) => idLookup.get(hash)}
				.flatten
			Some(ImmutableRoaringBitmap.bitmapOf(objIndices*))

		case CategFilter(category, values) =>
			val perValue = categMap(category)
			or(values.map(v => perValue.getOrElse(v, emptyBitmap)))

		case GeneralCategFilter(category, condition) => or(
			categMap(category).collect{
				case (cat, bm) if condition(cat) => bm
			}.toSeq
		)

		case gf: GeoFilter =>
			geoFiltering(gf, None)

		case Or(filters) =>
			collectUnless(filters.iterator.map(filtering))(_.isEmpty).flatMap{bmOpts =>
				or(bmOpts.flatten)
			}

		case All =>
			None
		case Nothing =>
			Some(emptyBitmap)
	}

	private def andFiltering(filters: Seq[Filter]): Option[ImmutableRoaringBitmap] =
		collectUnless(filters.iterator.flatMap(filtering))(_.isEmpty) match
			case None => Some(emptyBitmap)
			case Some(bms) => and(bms)

	private def geoFiltering(filter: GeoFilter, andFilter: Option[ImmutableRoaringBitmap]): Option[ImmutableRoaringBitmap] =
		geo.value match
			case None =>
				throw MetadataException("Geo index is not ready, please try again in a few minutes")
			case Some(Success(geoIndex)) => filter.property match
				case GeoIntersects => Some(geoIndex.getFilter(filter.geo, andFilter))
			case Some(Failure(exc)) =>
				throw Exception("Geo indexing failed", exc)

	private def negate(bm: ImmutableRoaringBitmap) =
		if objs.length == 0 then emptyBitmap else ImmutableRoaringBitmap.flip(bm, 0, objs.length.toLong)

	private def collectUnless[T](iter: Iterator[T])(cond: T => Boolean): Option[Seq[T]] = {
		var condHappened = false
		val seq = iter.takeWhile(elem => {
			condHappened = cond(elem)
			!condHappened
		}).toIndexedSeq
		if(condHappened) None else Some(seq)
	}
	private def or(bms: Seq[ImmutableRoaringBitmap]): Option[MutableRoaringBitmap] =
		if(bms.isEmpty) Some(emptyBitmap) else Some(BufferFastAggregation.or(bms*))

	private def and(bms: Seq[ImmutableRoaringBitmap]): Option[MutableRoaringBitmap] =
		if(bms.isEmpty) None else Some(BufferFastAggregation.and(bms*))

	def statEntries(filter: Filter): Iterable[StatEntry] = readLocked{
		log.debug(s"Fetching statEntries with Filter $filter")
		val filterOpt: Option[ImmutableRoaringBitmap] = filtering(filter)
		filterOpt match
			case None => log.debug("Fetching statEntries with no filter")
			case Some(bm) => log.debug(s"Fetching stat entries with filter permitting ${bm.getCardinality} objects")

		stats.flatMap{
			case (key, bm) =>
				val count = filterOpt.fold(bm.getCardinality)(ImmutableRoaringBitmap.andCardinality(bm, _))
				if key.spec.toString.contains("fieldsites") then
					log.debug(s"Count was $count for key $key")
				if(count > 0) Some(StatEntry(key, count))
				else None
		}
	}

	def lookupObject(hash: Sha256Sum): Option[ObjInfo] = idLookup.get(hash).map(objs.apply)

	def getObjEntry(hash: Sha256Sum): ObjEntry = idLookup.get(hash).fold{
			val canonicalHash = hash.truncate
			val oe = new ObjEntry(canonicalHash, objs.length, "")
			objs += oe
			idLookup += canonicalHash -> oe.idx
			oe
		}(objs.apply)

	def put(st: RdfUpdate): Unit = {
		q.put(st)
		if(q.remainingCapacity == 0) flush()
	}

	def flush(): Unit = if !q.isEmpty then writeLocked:
		if !q.isEmpty then
			val list = new ArrayList[RdfUpdate](UpdateQueueSize)
			q.drainTo(list)
			sail.accessEagerly:
				list.forEach:
					case RdfUpdate(Rdf4jStatement(subj, pred, obj), isAssertion) =>
						processUpdate(subj, pred, obj, isAssertion)
					case _ => ()
			list.clear()


	private def processUpdate(subj: IRI, pred: IRI, obj: Value, isAssertion: Boolean)(using GlobConn): Unit = {
		data.processTriple(
			subj,
			pred,
			obj,
			vocab,
			isAssertion,
			TriplestoreConnection.getStatements,
			TriplestoreConnection.hasStatement,
			nextVersCollIsComplete
		)
	}

	private def modForDobj[T](dobj: Value)(mod: ObjEntry => T): Option[T] = dobj match
		case CpVocab.DataObject(hash, prefix) =>
			val entry = getObjEntry(hash)
			if(entry.prefix == "") entry.prefix = prefix.intern()
			Some(mod(entry))

		case _ => None

	private def nextVersCollIsComplete(obj: IRI)(using GlobConn): Boolean =
		TriplestoreConnection.getStatements(obj, vocab.dcterms.hasPart, null)
			.collect:
				case Rdf4jStatement(_, _, member: IRI) => modForDobj(member){oe =>
					oe.isNextVersion = true
					oe.size > -1
				}
			.flatten
			.toIndexedSeq
			.exists(identity)

end CpIndex

object CpIndex:
	val UpdateQueueSize = 1 << 13


end CpIndex
