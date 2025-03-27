package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import org.eclipse.rdf4j.sail.Sail
import org.roaringbitmap.buffer.{BufferFastAggregation, ImmutableRoaringBitmap}
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.{RdfUpdate, StatementSource}
import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.services.sparql.magic.index.{IndexData, StatEntry}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.utils.async.ReadWriteLocking
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.io.Serializable
import java.time.Instant
import java.util.ArrayList
import java.util.concurrent.ArrayBlockingQueue
import scala.concurrent.Future
import scala.jdk.CollectionConverters.IteratorHasAsScala

import CpIndex.*
import se.lu.nateko.cp.meta.services.sparql.magic.index.ObjEntry

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

class CpIndex(sail: Sail, geo: Future[GeoIndex], data: IndexData) extends ReadWriteLocking:
	private val log = LoggerFactory.getLogger(getClass())
	private val filtering = Filtering(data, geo)

	import data.{contMap, stats, initOk}
	def this(sail: Sail, geo: Future[GeoIndex], nObjects: Int = 10000) = {
		this(sail, geo, IndexData(nObjects)())
		//Mass-import of the statistics data
		var statementCount = 0
		sail.accessEagerly:
			StatementSource.getStatements(null, null, null)
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

	private val queue = new ArrayBlockingQueue[RdfUpdate](UpdateQueueSize)

	def size: Int = data.objectCount
	def serializableData: Serializable = data

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
		idxIter.map(data.getObject)
	}


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

	def lookupObject(hash: Sha256Sum): Option[ObjEntry] = {
		data.getObjectId(hash).map(data.getObject)
	}

	def getObjEntry: Sha256Sum => ObjEntry = {
		data.getObjEntry
	}

	def put(st: RdfUpdate): Unit = {
		queue.put(st)
		if(queue.remainingCapacity == 0) flush()
	}

	def flush(): Unit = if !queue.isEmpty then writeLocked:
		if !queue.isEmpty then
			val list = new ArrayList[RdfUpdate](UpdateQueueSize)
			queue.drainTo(list)
			sail.accessEagerly:
				list.forEach:
					case RdfUpdate(Rdf4jStatement(statement), isAssertion) =>
						data.processUpdate(statement, isAssertion, vocab)
					case _ => ()
			list.clear()
end CpIndex


object CpIndex:
	val UpdateQueueSize = 1 << 13

