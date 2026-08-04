package se.lu.nateko.cp.meta.keyword

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import org.eclipse.rdf4j.model.{IRI, Literal, Statement, ValueFactory}
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection}

import java.net.URI
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

/**
 * Materializes `hasKeyword` (singular) triples onto data and document objects and
 * writes them into a dedicated derived named graph.
 *
 * This replaces the lookup support that the deleted "magic index" used to provide.
 * The index treated an object as carrying a keyword if it appeared in any of three
 * sources, unioned together: the object's own keywords, its spec's (`hasObjectSpec`),
 * and the spec's project's (`hasAssociatedProject`). With a remote SPARQL backend we
 * cannot expand that inherited union at query-evaluation time, so we materialize one
 * `hasKeyword` triple per distinct inherited keyword onto each object ahead of time
 * instead.
 *
 * The keywords are read from the singular `hasOwnKeyword` triples that
 * [[KeywordSplitter]] produced out of the legacy comma-separated `hasKeywords`
 * literals; nothing is parsed or split here.
 *
 * The derived graph is treated as a cache: `materializeAll` clears and rebuilds it,
 * making each run idempotent.
 */
class KeywordMaterializer(
	repo: Repository,
	derivedGraph: URI
)(using system: ActorSystem):

	private val log = Logging.getLogger(system, this)
	private given factory: ValueFactory = repo.getValueFactory
	private val graphIri: IRI = factory.createIRI(derivedGraph.toString)
	private val hasKeyword = factory.createIRI("http://meta.icos-cp.eu/ontologies/cpmeta/hasKeyword")
	private val stringDatatype = factory.createIRI("http://www.w3.org/2001/XMLSchema#string")

	private val DataObjectClass = "http://meta.icos-cp.eu/ontologies/cpmeta/DataObject"
	private val DocumentObjectClass = "http://meta.icos-cp.eu/ontologies/cpmeta/DocumentObject"
	private val HasOwnKeyword = "http://meta.icos-cp.eu/ontologies/cpmeta/hasOwnKeyword"
	private val HasObjectSpec = "http://meta.icos-cp.eu/ontologies/cpmeta/hasObjectSpec"
	private val HasAssociatedProject = "http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedProject"

	private val WriteBatchSize = 1000

	// Pagination guards against the triplestore silently truncating large result sets:
	// Virtuoso's /sparql endpoint caps rows at ResultSetMaxRows (commonly 10000) and can
	// time out heavy queries, in both cases returning a partial result with no error. We
	// therefore keep every individual query's result well under any such cap, in two
	// phases: enumerate object IRIs page by page, then fetch keywords for bounded batches
	// of those objects.
	//
	// Enumeration uses keyset (seek) pagination rather than LIMIT/OFFSET: Virtuoso refuses
	// an ORDER BY whose sorted window (OFFSET + LIMIT) exceeds MaxSortedTopRows (10000 by
	// default, error SR353), so deep OFFSETs fail outright. Carrying a `?obj > cursor`
	// filter instead keeps every page's sort to just ObjectPageSize rows. Both page sizes
	// must stay below the configured caps.
	private val ObjectPageSize = 5000   // object IRIs fetched per enumeration page
	private val KeywordBatchSize = 1000 // objects whose keywords are fetched per query

	def materializeAll()(using ExecutionContext): Future[Int] = Future:
		log.info(s"Keyword materialization started (graph $derivedGraph)")
		log.info(s"Clearing derived graph $derivedGraph")
		transact(_.clear(graphIri))
		log.info("Derived graph cleared; collecting keywords")
		val written = writeDerivedKeywords()
		log.info(s"Keyword materialization finished, wrote $written triples")
		written

	private def writeDerivedKeywords(): Int =
		val keywordsByObj = collectObjectKeywords()
		val totalKeywords = keywordsByObj.valuesIterator.map(_.size).sum
		log.info(
			s"Collected keywords for ${keywordsByObj.size} objects " +
			s"($totalKeywords keyword triples to write)"
		)
		writeInBatches(keywordsByObj)

	/**
	 * For every data/document object, the union of its own `hasOwnKeyword` keywords, its
	 * spec's and the spec's project's (the same three sources the magic index unioned),
	 * de-duplicated per object.
	 *
	 * Done in two paginated phases so that no single SPARQL result set can exceed the
	 * triplestore's row cap (or time out) and be silently truncated: first enumerate the
	 * object IRIs page by page, then fetch keywords for bounded batches of those objects.
	 */
	private def collectObjectKeywords(): mutable.Map[IRI, mutable.Set[String]] =
		val objects = listObjects()
		log.info(s"Enumerated ${objects.size} data/document objects; fetching keywords in batches of $KeywordBatchSize")

		val keywordsByObj = mutable.Map.empty[IRI, mutable.Set[String]]
		var done = 0
		for batch <- objects.grouped(KeywordBatchSize) do
			fetchKeywordsForBatch(batch, keywordsByObj)
			done += batch.size
			log.info(s"Fetched keywords for $done/${objects.size} objects (${keywordsByObj.size} have keywords so far)")

		log.info(s"Keyword collection finished: ${keywordsByObj.size} of ${objects.size} objects carry keywords")
		keywordsByObj

	/**
	 * All data/document object IRIs, read page by page with keyset (seek) pagination: each
	 * page asks for the next ObjectPageSize objects ordered after the previous page's last
	 * IRI. Unlike LIMIT/OFFSET this keeps every page's sorted window small (avoiding
	 * Virtuoso's MaxSortedTopRows limit) and never silently caps the enumeration.
	 */
	private def listObjects(): IndexedSeq[IRI] =
		val objects = ArrayBuffer.empty[IRI]
		var cursor = "" // STR of the last object IRI returned; "" sorts before every IRI
		var more = true

		log.info("Enumerating data/document objects")
		while more do
			val cursorLiteral = "\"" + cursor.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
			val q = s"""
				|SELECT DISTINCT ?obj WHERE {
				|  ?obj a ?t .
				|  FILTER(?t IN (<$DataObjectClass>, <$DocumentObjectClass>))
				|  FILTER(STR(?obj) > $cursorLiteral)
				|}
				|ORDER BY STR(?obj)
				|LIMIT $ObjectPageSize""".stripMargin

			var pageRows = 0
			val conn = repo.getConnection()
			try
				val result = conn.prepareTupleQuery(QueryLanguage.SPARQL, q).evaluate()
				try
					while result.hasNext() do
						pageRows += 1
						result.next().getValue("obj") match
							case obj: IRI =>
								objects += obj
								cursor = obj.stringValue
							case _ => ()
				finally result.close()
			finally conn.close()

			more = pageRows == ObjectPageSize
			log.info(s"Enumerated ${objects.size} objects so far")

		objects.toIndexedSeq

	/** Fetches own/spec/project `hasOwnKeyword` keywords for a bounded batch of objects in one query. */
	private def fetchKeywordsForBatch(
		batch: collection.Seq[IRI],
		acc: mutable.Map[IRI, mutable.Set[String]]
	): Unit =
		val values = batch.map(obj => s"<$obj>").mkString(" ")
		val q = s"""
			|SELECT ?obj ?keyword WHERE {
			|  VALUES ?obj { $values }
			|  {
			|    ?obj <$HasOwnKeyword> ?keyword .
			|  } UNION {
			|    ?obj <$HasObjectSpec> ?spec .
			|    ?spec <$HasOwnKeyword> ?keyword .
			|  } UNION {
			|    ?obj <$HasObjectSpec> ?spec .
			|    ?spec <$HasAssociatedProject> ?proj .
			|    ?proj <$HasOwnKeyword> ?keyword .
			|  }
			|}""".stripMargin

		val conn = repo.getConnection()
		try
			val result = conn.prepareTupleQuery(QueryLanguage.SPARQL, q).evaluate()
			try
				while result.hasNext() do
					val bindings = result.next()
					(bindings.getValue("obj"), bindings.getValue("keyword")) match
						case (obj: IRI, keyword: Literal) =>
							val kw = keyword.stringValue.trim
							if kw.nonEmpty then
								acc.getOrElseUpdate(obj, mutable.Set.empty) += kw
						case _ => ()
			finally result.close()
		finally conn.close()

	private def writeInBatches(keywordsByObj: mutable.Map[IRI, mutable.Set[String]]): Int =
		var total = 0
		val batch = ArrayBuffer.empty[Statement]

		def flush(): Unit =
			if batch.nonEmpty then
				val toWrite = batch.toIndexedSeq
				transact { conn =>
					for st <- toWrite do conn.add(st, graphIri)
				}
				total += toWrite.size
				batch.clear()
				log.info(s"Wrote batch of ${toWrite.size} triples (total $total written)")

		for (obj, kws) <- keywordsByObj; kw <- kws do
			batch += factory.createStatement(obj, hasKeyword, factory.createLiteral(kw, stringDatatype))
			if batch.size >= WriteBatchSize then flush()

		flush()
		log.info(s"Finished writing $total triples to $derivedGraph")
		total

	private def transact(action: RepositoryConnection => Unit): Unit =
		Using.resource(repo.getConnection()): conn =>
			conn.begin()
			try
				action(conn)
				conn.commit()
			catch
				case error: Throwable =>
					conn.rollback()
					throw error

end KeywordMaterializer
