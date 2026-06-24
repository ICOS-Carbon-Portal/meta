package se.lu.nateko.cp.meta.keyword

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import org.eclipse.rdf4j.model.{IRI, Statement, ValueFactory}
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}

/**
 * Materializes `hasKeyword` (singular) triples onto data and document objects and
 * writes them into a dedicated derived named graph.
 *
 * This replaces the lookup support that the deleted "magic index" used to provide.
 * The index treated an object as carrying a keyword if it appeared in any of three
 * sources, unioned together: the object's own `hasKeywords`, its spec's `hasKeywords`
 * (`hasObjectSpec`), and the spec's project's `hasKeywords` (`hasAssociatedProject`).
 * With a remote SPARQL backend we cannot expand that comma-separated, inherited list
 * at query-evaluation time, so we materialize one `hasKeyword` triple per distinct
 * inherited keyword onto each object ahead of time instead.
 *
 * The derived graph is treated as a cache: `materializeAll` clears and rebuilds it,
 * making each run idempotent.
 */
class KeywordMaterializer(
	repo: Repository,
	metaVocab: CpmetaVocab,
	derivedGraph: URI
)(using system: ActorSystem):

	private val log = Logging.getLogger(system, this)
	private given factory: ValueFactory = repo.getValueFactory
	private val graphIri: IRI = factory.createIRI(derivedGraph.toString)

	private val WriteBatchSize = 1000

	def materializeAll()(using ExecutionContext): Future[Int] = Future:
		log.info(s"Keyword materialization started (graph $derivedGraph)")
		log.info(s"Clearing derived graph $derivedGraph")
		repo.transact(_.clear(graphIri)).get
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
	 * For every data/document object, the union of its own keywords, its spec's
	 * keywords and the spec's project's keywords (the same three sources the magic
	 * index unioned). Comma-separated lists are parsed and de-duplicated per object.
	 */
	private def collectObjectKeywords(): mutable.Map[IRI, mutable.Set[String]] =
		val q = s"""
			|SELECT ?obj ?keywords WHERE {
			|  ?obj a ?t .
			|  FILTER(?t IN (<${metaVocab.dataObjectClass}>, <${metaVocab.docObjectClass}>))
			|  {
			|    ?obj <${metaVocab.hasKeywords}> ?keywords .
			|  } UNION {
			|    ?obj <${metaVocab.hasObjectSpec}> ?spec .
			|    ?spec <${metaVocab.hasKeywords}> ?keywords .
			|  } UNION {
			|    ?obj <${metaVocab.hasObjectSpec}> ?spec .
			|    ?spec <${metaVocab.hasAssociatedProject}> ?proj .
			|    ?proj <${metaVocab.hasKeywords}> ?keywords .
			|  }
			|}""".stripMargin

		val keywordsByObj = mutable.Map.empty[IRI, mutable.Set[String]]

		log.info("Querying triplestore for object/spec/project keywords")
		val conn = repo.getConnection()
		try
			val result = conn.prepareTupleQuery(QueryLanguage.SPARQL, q).evaluate()
			try
				var rows = 0
				while result.hasNext() do
					val bindings = result.next()
					rows += 1
					if rows % 10000 == 0 then
						log.info(s"Processed $rows result rows, ${keywordsByObj.size} objects so far")
					bindings.getValue("obj") match
						case obj: IRI =>
							val keywordsStr = Option(bindings.getValue("keywords")).fold("")(_.stringValue)
							val kws = parseCommaSepList(keywordsStr)
							if kws.nonEmpty then
								keywordsByObj.getOrElseUpdate(obj, mutable.Set.empty) ++= kws
						case _ => ()
				log.info(s"Query finished: $rows result rows across ${keywordsByObj.size} objects")
			finally result.close()
		finally conn.close()

		keywordsByObj

	private def writeInBatches(keywordsByObj: mutable.Map[IRI, mutable.Set[String]]): Int =
		var total = 0
		val batch = ArrayBuffer.empty[Statement]

		def flush(): Unit =
			if batch.nonEmpty then
				val toWrite = batch.toIndexedSeq
				repo.transact { conn =>
					for st <- toWrite do conn.add(st, graphIri)
				}.get
				total += toWrite.size
				batch.clear()
				log.info(s"Wrote batch of ${toWrite.size} triples (total $total written)")

		for (obj, kws) <- keywordsByObj; kw <- kws do
			batch += factory.createStatement(obj, metaVocab.hasKeyword, factory.createStringLiteral(kw))
			if batch.size >= WriteBatchSize then flush()

		flush()
		log.info(s"Finished writing $total triples to $derivedGraph")
		total

end KeywordMaterializer
