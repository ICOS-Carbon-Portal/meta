package se.lu.nateko.cp.meta.keyword

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import org.eclipse.rdf4j.model.{IRI, Literal}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.rio.helpers.NTriplesUtil
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.services.{CpmetaVocab, Rdf4jSparqlRunner}
import se.lu.nateko.cp.meta.utils.parseCommaSepList

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

/**
 * Replaces every `hasKeywords` (plural, comma-separated) literal in the triplestore with
 * one `hasKeyword` (singular) triple per distinct keyword on the very same subject, in the
 * very same named graph.
 *
 * Unlike [[KeywordMaterializer]] this does no inheritance: it neither restricts subjects to
 * data/document objects nor unions in a spec's or project's keywords. It simply expands
 * whatever `?s hasKeywords "a, b"` triples exist anywhere in the store into `?s hasKeyword
 * "a"`, `?s hasKeyword "b"` on the same `?s`, and then removes the `hasKeywords` triple.
 *
 * The replacement is a migration, not a cache: nothing is written to a derived graph and no
 * graph is ever cleared. Each subject is handled write-verify-delete, one bounded batch at a
 * time:
 *
 *   1. write the singular triples into the source graph,
 *   2. read them back from the store and confirm every expected keyword is present,
 *   3. only then delete the `hasKeywords` triple(s) they came from.
 *
 * A batch whose read-back does not confirm keeps its `hasKeywords` triples, so an
 * interrupted or partially failing run never loses keywords, and re-running picks up
 * exactly what is left. Re-adding an already-present `hasKeyword` triple is a no-op in RDF,
 * which makes the whole pass idempotent.
 *
 * Both the writing and the deleting are done with SPARQL updates we build ourselves rather
 * than through `RepositoryConnection.add`/`remove`, because rdf4j's literals are not
 * interchangeable with Virtuoso's: see [[plainLiteral]].
 *
 * A `hasKeywords` literal that parses to no keywords at all (empty, or only separators) has
 * no singular counterpart to confirm, so it is reported and left untouched rather than
 * deleted without replacement.
 */
class KeywordSplitter(
	repo: Repository,
	metaVocab: CpmetaVocab
)(using system: ActorSystem):
	import KeywordSplitter.{SplitSummary, SubjectKeywords}

	private val log = Logging.getLogger(system, this)
	private val sparql = new Rdf4jSparqlRunner(repo)

	// Pagination guards against the triplestore silently truncating large result sets:
	// Virtuoso's /sparql endpoint caps rows at ResultSetMaxRows (commonly 10000) and can
	// time out heavy queries, in both cases returning a partial result with no error. We
	// therefore keep every individual query's result well under any such cap, in two
	// phases: enumerate the subjects that carry `hasKeywords` page by page, then handle
	// bounded batches of those subjects.
	//
	// Enumeration uses keyset (seek) pagination rather than LIMIT/OFFSET: Virtuoso refuses
	// an ORDER BY whose sorted window (OFFSET + LIMIT) exceeds MaxSortedTopRows (10000 by
	// default, error SR353), so deep OFFSETs fail outright. Carrying a `?subj > cursor`
	// filter instead keeps every page's sort to just SubjectPageSize rows. Both sizes must
	// stay below the configured caps.
	private val SubjectPageSize = 5000  // subject IRIs fetched per enumeration page
	private val SubjectBatchSize = 500  // subjects written, verified and deleted per batch

	def splitAll()(using ExecutionContext): Future[SplitSummary] = Future:
		log.info("Keyword splitting started")
		val subjects = listSubjects()
		log.info(s"Enumerated ${subjects.size} subjects with hasKeywords; processing in batches of $SubjectBatchSize")

		var summary = SplitSummary.empty
		var done = 0
		for batch <- subjects.grouped(SubjectBatchSize) do
			summary = summary + processBatch(batch)
			done += batch.size
			log.info(s"Processed $done/${subjects.size} subjects: $summary")

		log.info(s"Keyword splitting finished: $summary")
		if summary.unconfirmed.nonEmpty then
			log.warning(
				s"${summary.unconfirmed.size} subjects kept their hasKeywords because the " +
				s"written hasKeyword triples could not be confirmed: " +
				summary.unconfirmed.take(20).mkString(", ")
			)
		if summary.empties.nonEmpty then
			log.warning(
				s"${summary.empties.size} subjects have a hasKeywords literal with no parseable " +
				s"keyword and were left untouched: " + summary.empties.take(20).mkString(", ")
			)
		summary

	/**
	 * All subject IRIs carrying a `hasKeywords` property, read page by page with keyset
	 * (seek) pagination: each page asks for the next SubjectPageSize subjects ordered after
	 * the previous page's last IRI. Unlike LIMIT/OFFSET this keeps every page's sorted
	 * window small (avoiding Virtuoso's MaxSortedTopRows limit) and never silently caps the
	 * enumeration.
	 */
	private def listSubjects(): IndexedSeq[IRI] =
		val subjects = ArrayBuffer.empty[IRI]
		var cursor = "" // STR of the last subject IRI returned; "" sorts before every IRI
		var more = true

		log.info("Enumerating subjects with hasKeywords")
		while more do
			val q = s"""
				|SELECT DISTINCT ?subj WHERE {
				|  GRAPH ?g { ?subj ${iriRef(metaVocab.hasKeywords)} ?keywords . }
				|  FILTER(STR(?subj) > ${plainLiteral(cursor)})
				|}
				|ORDER BY STR(?subj)
				|LIMIT $SubjectPageSize""".stripMargin

			var pageRows = 0
			select(q): bindings =>
				pageRows += 1
				bindings.getValue("subj") match
					case subj: IRI =>
						subjects += subj
						cursor = subj.stringValue
					case _ => ()

			more = pageRows == SubjectPageSize
			log.info(s"Enumerated ${subjects.size} subjects so far")

		subjects.toIndexedSeq

	/** Write, verify, then delete, for one bounded batch of subjects. */
	private def processBatch(batch: collection.Seq[IRI]): SplitSummary =
		val (groups, empties) = fetchKeywords(batch)
		val emptySummary = SplitSummary.empty.copy(empties = empties)
		if groups.isEmpty then emptySummary
		else
			val written = writeKeywords(groups)
			val (confirmed, unconfirmed) = verifyKeywords(groups)
			val deleted = deleteSources(confirmed)
			emptySummary.copy(
				subjects = confirmed.map(_.subj).distinct.size,
				written = written,
				deleted = deleted,
				unconfirmed = unconfirmed.map(_.subj).distinct
			)

	/**
	 * The `hasKeywords` literals of a bounded batch of subjects, per source graph, together
	 * with the distinct keywords parsed out of them. The second element lists the subjects
	 * whose literals hold no parseable keyword at all.
	 */
	private def fetchKeywords(batch: collection.Seq[IRI]): (IndexedSeq[SubjectKeywords], IndexedSeq[IRI]) =
		val q = s"""
			|SELECT ?subj ?g ?keywords WHERE {
			|  VALUES ?subj { ${batch.map(iriRef).mkString(" ")} }
			|  GRAPH ?g { ?subj ${iriRef(metaVocab.hasKeywords)} ?keywords . }
			|}""".stripMargin

		// several hasKeywords literals may sit on the same subject in the same graph, and
		// the same subject may occur in more than one graph; each (subject, graph) pair is
		// migrated on its own, within that graph
		val acc = mutable.LinkedHashMap.empty[(IRI, IRI), (ArrayBuffer[Literal], mutable.LinkedHashSet[String])]

		select(q): bindings =>
			(bindings.getValue("subj"), bindings.getValue("g"), bindings.getValue("keywords")) match
				case (subj: IRI, graph: IRI, keywords: Literal) =>
					val (sources, kws) = acc.getOrElseUpdate(
						subj -> graph,
						ArrayBuffer.empty[Literal] -> mutable.LinkedHashSet.empty[String]
					)
					sources += keywords
					kws ++= parseCommaSepList(keywords.stringValue)
				case _ => ()

		val groups = ArrayBuffer.empty[SubjectKeywords]
		val empties = ArrayBuffer.empty[IRI]
		for ((subj, graph), (sources, kws)) <- acc do
			if kws.isEmpty then empties += subj
			else groups += SubjectKeywords(subj, graph, sources.toIndexedSeq, kws.toIndexedSeq)

		groups.toIndexedSeq -> empties.toIndexedSeq.distinct

	/**
	 * Adds the singular triples to the graph their `hasKeywords` source lives in, one
	 * `INSERT DATA` per graph, with the keywords as plain literals (see [[plainLiteral]]).
	 */
	private def writeKeywords(groups: IndexedSeq[SubjectKeywords]): Int =
		var written = 0
		for (graph, inGraph) <- groups.groupBy(_.graph) do
			val triples = for group <- inGraph; kw <- group.keywords
				yield s"${iriRef(group.subj)} ${iriRef(metaVocab.hasKeyword)} ${plainLiteral(kw)} ."

			update(s"""
				|INSERT DATA { GRAPH ${iriRef(graph)} {
				|${triples.mkString("\n")}
				|} }""".stripMargin)
			written += triples.size

		log.debug(s"Wrote $written hasKeyword triples for ${groups.size} subject/graph pairs")
		written

	/**
	 * Reads the singular triples back and partitions the groups into those whose every
	 * expected keyword is now present in the store, and those where something is missing.
	 * Only the former may have their `hasKeywords` source deleted.
	 */
	private def verifyKeywords(
		groups: IndexedSeq[SubjectKeywords]
	): (IndexedSeq[SubjectKeywords], IndexedSeq[SubjectKeywords]) =
		val found = mutable.Map.empty[(IRI, IRI), mutable.Set[String]]

		// one query per source graph, with the graph IRI concrete, so that read-back is a
		// plain lookup in the same graph the triples were just written to
		for (graph, inGraph) <- groups.groupBy(_.graph) do
			val q = s"""
				|SELECT ?subj ?kw WHERE {
				|  VALUES ?subj { ${inGraph.map(g => iriRef(g.subj)).mkString(" ")} }
				|  GRAPH ${iriRef(graph)} { ?subj ${iriRef(metaVocab.hasKeyword)} ?kw . }
				|}""".stripMargin

			select(q): bindings =>
				(bindings.getValue("subj"), bindings.getValue("kw")) match
					case (subj: IRI, kw: Literal) =>
						found.getOrElseUpdate(subj -> graph, mutable.Set.empty) += kw.stringValue
					case _ => ()

		groups.partition: group =>
			val present = found.getOrElse(group.subj -> group.graph, mutable.Set.empty)
			val missing = group.keywords.filterNot(present.contains)
			if missing.isEmpty then true
			else
				log.warning(
					s"Not deleting hasKeywords of <${group.subj}> in graph <${group.graph}>: " +
					s"${missing.size} of ${group.keywords.size} keywords absent after write " +
					s"(${missing.take(5).mkString(", ")})"
				)
				false

	/**
	 * Deletes the `hasKeywords` statements the confirmed keywords came from, one
	 * `DELETE ... WHERE` per graph. The statements are matched by subject and predicate, with
	 * the literal left as a variable, rather than by their exact term: Virtuoso would not
	 * match a term we send back to it (see [[plainLiteral]]), and every `hasKeywords` literal
	 * of a confirmed subject in that graph was read and split by [[fetchKeywords]] anyway.
	 *
	 * The count returned is what the store actually lost, established by counting the
	 * remaining `hasKeywords` of the same subjects afterwards, so that a delete which matches
	 * nothing cannot be reported as a success.
	 */
	private def deleteSources(confirmed: IndexedSeq[SubjectKeywords]): Int =
		var deleted = 0
		for (graph, inGraph) <- confirmed.groupBy(_.graph) do
			val subjFilter = s"FILTER(?subj IN (${inGraph.map(g => iriRef(g.subj)).mkString(", ")}))"
			val pattern = s"GRAPH ${iriRef(graph)} { ?subj ${iriRef(metaVocab.hasKeywords)} ?keywords . }"

			update(s"""
				|DELETE { $pattern }
				|WHERE { $pattern $subjFilter }""".stripMargin)

			var remaining = 0
			select(s"""
				|SELECT ?subj ?keywords WHERE {
				|  $pattern
				|  $subjFilter
				|}""".stripMargin)(_ => remaining += 1)

			val expected = inGraph.map(_.sources.size).sum
			deleted += expected - remaining
			if remaining > 0 then log.warning(
				s"$remaining of $expected hasKeywords statements in graph <$graph> survived " +
				s"their deletion; re-running will retry them"
			)

		log.debug(s"Deleted $deleted hasKeywords triples")
		deleted

	private def select(query: String)(handleRow: BindingSet => Unit): Unit =
		Using.resource(sparql.evaluateTupleQuery(SparqlQuery(query)))(_.foreach(handleRow))

	/**
	 * Runs a SPARQL update. Note that we write the updates ourselves instead of using
	 * `RepositoryConnection.add`/`remove`, whose statements rdf4j would serialize for us: see
	 * [[plainLiteral]] for why their literals cannot be matched by Virtuoso.
	 */
	private def update(query: String): Unit = Using.resource(repo.getConnection()):
		_.prepareUpdate(QueryLanguage.SPARQL, query).execute()

	/**
	 * A string as a SPARQL plain literal, that is, without the `^^xsd:string` datatype.
	 *
	 * The two are the same term in RDF 1.1, and rdf4j treats them as such, but Virtuoso does
	 * not: a plain literal in the store is matched neither by `"kw"^^xsd:string` in a DELETE
	 * nor in a query, and vice versa, silently and without error. rdf4j, on the other hand,
	 * serializes string literals *with* the datatype: its
	 * `BasicWriterSettings.XSD_STRING_TO_PLAIN_LITERAL` defaults to false, so going through
	 * `RepositoryConnection.add`/`remove` would both write `hasKeyword` triples that the
	 * plain-literal queries of the portal cannot find, and delete nothing at all — the
	 * `hasKeywords` literals in the store are plain.
	 *
	 * The escaping of the label itself is rdf4j's, in the variant that escapes non-ASCII and
	 * non-printable characters as `\\uXXXX`, so that not even the control characters our data
	 * is known to contain can break the query.
	 */
	private def plainLiteral(value: String): String =
		"\"" + NTriplesUtil.escapeString(value) + "\""

	/** An IRI in the `<...>` form, escaped by rdf4j. */
	private def iriRef(iri: IRI): String = NTriplesUtil.toNTriplesString(iri)

end KeywordSplitter

object KeywordSplitter:

	/** The `hasKeywords` literals of one subject in one graph, and the keywords in them. */
	private case class SubjectKeywords(
		subj: IRI,
		graph: IRI,
		sources: IndexedSeq[Literal],
		keywords: IndexedSeq[String]
	)

	case class SplitSummary(
		subjects: Int,
		written: Int,
		deleted: Int,
		unconfirmed: IndexedSeq[IRI],
		empties: IndexedSeq[IRI]
	):
		def +(other: SplitSummary) = SplitSummary(
			subjects = subjects + other.subjects,
			written = written + other.written,
			deleted = deleted + other.deleted,
			unconfirmed = unconfirmed ++ other.unconfirmed,
			empties = empties ++ other.empties
		)

		override def toString =
			s"$subjects subjects split, $written hasKeyword written, $deleted hasKeywords deleted, " +
			s"${unconfirmed.size} unconfirmed, ${empties.size} without parseable keyword"

	object SplitSummary:
		val empty = SplitSummary(0, 0, 0, Vector.empty, Vector.empty)

end KeywordSplitter
