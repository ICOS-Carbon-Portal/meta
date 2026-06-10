 package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
 * Writes the three "magic" virtual triples (hasBiblioInfo, hasCitationString,
 * dcterms:license) as real triples into a dedicated derived named graph.
 *
 * Replaces the on-the-fly enrichment that used to live in EnrichingSail /
 * StatementsEnricher: with a remote SPARQL backend we cannot inject values at
 * query-evaluation time, so we materialize them ahead of time instead.
 *
 * `materializeAll` is incremental: it leaves the derived graph in place and only
 * materializes citations for citable subjects that do not already have any triple
 * in the derived graph. This avoids re-fetching from DataCite on every run.
 *
 * Reading the metadata needed to compute the citations goes through a
 * [[StatementCache]]: each batch of subjects is warmed up with a few bulk SPARQL
 * queries, and the readers then run against the cache instead of issuing one
 * remote SPARQL request per statement pattern.
 *
 * Subjects whose citation needs DataCite data that is not cached yet are not
 * materialized in the main pass: they are pushed to a [[DataCiteQueue]] (which
 * triggers the lookups immediately) and materialized, in push order, as their
 * DataCite lookups complete, concurrently with the main pass. A run finishes
 * when both the main pass and the queue are done.
 */
class CitationMaterializer(
	repo: Repository,
	citer: CitationProvider,
	derivedGraph: URI
)(using system: ActorSystem):

	private val log = Logging.getLogger(system, this)
	private given factory: ValueFactory = repo.getValueFactory
	private val graphIri: IRI = factory.createIRI(derivedGraph.toString)
	import citer.metaVocab

	private val WriteBatchSize = 1000

	def materializeAll()(using ExecutionContext): Future[Int] =
		val startNanos = System.nanoTime()
		val cache = new StatementCache(repo, log)
		val cacheConn = new CachingConnection(cache)
		val dcQueue = new DataCiteQueue(citer.doiCiter, subjs => writeDataCiteBatch(subjs, cache, cacheConn))

		val mainPass = Future:
			log.info(s"Citation materialization started (graph $derivedGraph)")
			log.info("Listing citable subjects...")
			val listStartNanos = System.nanoTime()
			val allSubjects = listCitableSubjects()
			val alreadyMaterialized = listMaterializedSubjects()
			val subjects = allSubjects.filterNot(alreadyMaterialized.contains)
			log.info(
				f"Found ${allSubjects.size} citable subjects in ${(System.nanoTime() - listStartNanos) / 1e9}%.1f s " +
				f"(${alreadyMaterialized.size} already materialized, ${subjects.size} to materialize), materializing citations..."
			)
			writeInBatches(subjects, cache, cacheConn, dcQueue)

		mainPass.transformWith:
			case scala.util.Failure(err) =>
				// always shut the queue down, so no stream outlives the run
				dcQueue.drain().transform(_ => scala.util.Failure(err))
			case Success(mainWritten) =>
				if dcQueue.pending > 0 then log.info(
					s"Main pass done, wrote $mainWritten triples; waiting for the DataCite queue (${dcQueue.pending} subjects pending)"
				)
				dcQueue.drain().map: queueWritten =>
					val written = mainWritten + queueWritten
					val skipped =
						if dcQueue.failed + dcQueue.dropped == 0 then ""
						else s" (${dcQueue.failed} subjects skipped on DataCite failures, ${dcQueue.dropped} dropped from the queue)"
					log.info(
						f"Citation materialization finished, wrote $written triples " +
						f"($queueWritten via the DataCite queue)$skipped in ${(System.nanoTime() - startNanos) / 1e9}%.0f s"
					)
					written

	private def listCitableSubjects(): IndexedSeq[IRI] =
		val q = s"""
			|SELECT DISTINCT ?s WHERE {
			|  ?s a ?t .
			|  FILTER(?t IN (<${metaVocab.dataObjectClass}>, <${metaVocab.docObjectClass}>, <${metaVocab.collectionClass}>))
			|}""".stripMargin
		querySubjects(q).toIndexedSeq

	/** Subjects that already have at least one triple in the derived citations graph. */
	private def listMaterializedSubjects(): Set[IRI] =
		val q = s"""
			|SELECT DISTINCT ?s WHERE {
			|  GRAPH <$derivedGraph> { ?s ?p ?o }
			|}""".stripMargin
		querySubjects(q).toSet

	private def querySubjects(query: String): collection.Seq[IRI] =
		val conn = repo.getConnection()
		try
			val result = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
			val buf = ArrayBuffer.empty[IRI]
			try
				while result.hasNext() do
					result.next().getValue("s") match
						case iri: IRI => buf += iri
						case _ => ()
			finally result.close()
			buf
		finally conn.close()

	private def writeInBatches(
		subjects: IndexedSeq[IRI], cache: StatementCache, cacheConn: CachingConnection, dcQueue: DataCiteQueue
	): Int =
		val total = subjects.size
		val batchCount = (total + WriteBatchSize - 1) / WriteBatchSize
		val startNanos = System.nanoTime()
		var totalWritten = 0
		var totalDeferred = 0
		var processed = 0
		subjects.zipWithIndex.grouped(WriteBatchSize).zipWithIndex.foreach: (indexedChunk, batchIdx) =>
			val batchStartNanos = System.nanoTime()
			val statsBefore = cache.stats
			cache.prefetch(indexedChunk.map(_._1))
			val triples = ArrayBuffer.empty[Statement]
			var deferred = 0
			for (subj, idx) <- indexedChunk do
				subjectDoi(subj, cacheConn) match
					case Some(doi) if !dataCiteReady(doi) =>
						dcQueue.push(subj, doi)
						deferred += 1
						log.debug(s"[${idx + 1}/$total] Deferred $subj to the DataCite queue (DOI $doi)")
					case _ =>
						triples ++= triplesFor(subj, s"${idx + 1}/$total", cacheConn)
			repo.transact: conn =>
				for t <- triples do conn.add(t, graphIri)
			totalWritten += triples.size
			totalDeferred += deferred
			processed += indexedChunk.size
			val batchMs = (System.nanoTime() - batchStartNanos) / 1000000
			val elapsedS = (System.nanoTime() - startNanos) / 1e9
			val rate = if elapsedS > 0 then processed / elapsedS else 0.0
			val etaS = if rate > 0 then (total - processed) / rate else 0.0
			log.info(
				f"Citation materialization progress: batch ${batchIdx + 1}/$batchCount in ${batchMs} ms, " +
				f"$processed/$total subjects processed (${100.0 * processed / total}%.1f%%), " +
				f"$totalWritten triples written so far, $totalDeferred deferred to the DataCite queue, " +
				f"${elapsedS}%.0f s elapsed, ${rate}%.1f subj/s, ETA ${etaS}%.0f s; sparql: ${cache.stats.minus(statsBefore)}"
			)
		totalWritten

	/** Materializes a batch of subjects coming back from the DataCite queue; runs on the
	 *  queue's stream, concurrently with the main pass. */
	private def writeDataCiteBatch(subjs: Seq[IRI], cache: StatementCache, cacheConn: CachingConnection): Int =
		cache.prefetch(subjs)
		val triples = subjs.flatMap(subj => triplesFor(subj, "DataCite queue", cacheConn)).toIndexedSeq
		repo.transact: conn =>
			for t <- triples do conn.add(t, graphIri)
		.get
		log.info(s"DataCite queue: materialized ${subjs.size} subjects (${triples.size} triples)")
		triples.size

	/** The first valid DOI of the subject, if any. */
	private def subjectDoi(subj: IRI, conn: CachingConnection): Option[Doi] =
		StatementSource.getStringValues(subj, metaVocab.hasDoi)(using conn)
			.flatMap(Doi.parse(_).toOption)
			.headOption

	/** Whether all DataCite lookups needed to cite the given DOI have already succeeded.
	 *  Triggers (throttled, asynchronous) lookups of whatever is missing as a side
	 *  effect, so a deferred subject's DataCite data is being fetched while it waits
	 *  in the queue. */
	private def dataCiteReady(doi: Doi): Boolean =
		import citer.doiCiter
		val citationsReady = DataCiteQueue.NeededStyles
			.map(style => doiCiter.getCitationEager(doi, style))
			.forall:
				case Some(Success(_)) => true
				case _ => false
		val doiMetaReady = doiCiter.getDoiEager(doi) match
			case Some(Success(_)) => true
			case _ => false
		citationsReady && doiMetaReady

	private def triplesFor(subj: IRI, tag: String, conn: CachingConnection): IndexedSeq[Statement] =
		val startNanos = System.nanoTime()
		log.debug(s"[$tag] Materializing citation for $subj ...")
		val out = ArrayBuffer.empty[Statement]

		val refs: Option[References] = citer.getReferences(subj, conn)
		val hasBiblio = biblioLiteral(refs).map: lit =>
			out += factory.createStatement(subj, metaVocab.hasBiblioInfo, lit)
		.isDefined

		val citationOpt = refs.fold(citer.getCitation(subj, conn))(_.citationString)
		citationOpt.foreach: cit =>
			out += factory.createStatement(subj, metaVocab.hasCitationString, factory.createStringLiteral(cit))

		val licenceOpt = refs.fold(citer.getLicence(subj, conn))(_.licence)
		licenceOpt.foreach: lic =>
			out += factory.createStatement(subj, metaVocab.dcterms.license, lic.url.toRdf)

		val durMs = (System.nanoTime() - startNanos) / 1000000
		if out.isEmpty then
			log.info(s"[$tag] No citation triples produced for $subj (${durMs} ms)")
		else
			log.info(
				s"[$tag] Materialized ${out.size} triples for $subj in ${durMs} ms " +
				s"[biblio=$hasBiblio, citation=${citationOpt.isDefined}, licence=${licenceOpt.isDefined}]"
			)
		out.toIndexedSeq

	private def biblioLiteral(refs: Option[References]): Option[Value] =
		import spray.json.*
		import se.lu.nateko.cp.meta.core.data.JsonSupport.{given RootJsonFormat[References]}
		refs.map(js => factory.createStringLiteral(js.toJson.compactPrint))

end CitationMaterializer
