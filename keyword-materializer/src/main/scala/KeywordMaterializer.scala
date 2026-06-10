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
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}

/**
 * Derives `hasKeyword` (singular) triples from the `hasKeywords` (plural) string
 * lists that live in the Virtuoso triplestore, and writes them into a dedicated
 * derived named graph.
 *
 * This replaces the lookup support that the deleted "magic index" used to provide:
 * with a remote SPARQL backend we cannot expand the comma-separated keyword list
 * at query-evaluation time, so we materialize one triple per individual keyword
 * ahead of time instead.
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
		repo.transact(_.clear(graphIri)).get
		val written = writeDerivedKeywords()
		log.info(s"Keyword materialization finished, wrote $written triples")
		written

	private def writeDerivedKeywords(): Int =
		val q = s"""
			|SELECT ?s ?keywords WHERE {
			|  ?s <${metaVocab.hasKeywords}> ?keywords .
			|}""".stripMargin

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

		val conn = repo.getConnection()
		try
			val result = conn.prepareTupleQuery(QueryLanguage.SPARQL, q).evaluate()
			try
				while result.hasNext() do
					val bindings = result.next()
					bindings.getValue("s") match
						case subj: IRI =>
							val keywordsStr = Option(bindings.getValue("keywords")).fold("")(_.stringValue)
							for kw <- parseCommaSepList(keywordsStr) do
								batch += factory.createStatement(subj, metaVocab.hasKeyword, factory.createStringLiteral(kw))
								if batch.size >= WriteBatchSize then flush()
						case _ => ()
			finally result.close()
		finally conn.close()

		flush()
		total

end KeywordMaterializer
