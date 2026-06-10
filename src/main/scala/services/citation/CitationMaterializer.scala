package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}

/**
 * Writes the three "magic" virtual triples (hasBiblioInfo, hasCitationString,
 * dcterms:license) as real triples into a dedicated derived named graph.
 *
 * Replaces the on-the-fly enrichment that used to live in EnrichingSail /
 * StatementsEnricher: with a remote SPARQL backend we cannot inject values at
 * query-evaluation time, so we materialize them ahead of time instead.
 *
 * The derived graph is treated as a cache: it is cleared and rebuilt by
 * `materializeAll`.
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

	def materializeAll()(using ExecutionContext): Future[Int] = Future:
		log.info(s"Citation materialization started (graph $derivedGraph)")
		repo.transact(_.clear(graphIri))
		val subjects = listCitableSubjects()
		log.info(s"Materializing citations for ${subjects.size} subjects")
		val written = writeInBatches(subjects)
		log.info(s"Citation materialization finished, wrote $written triples")
		written

	private def listCitableSubjects(): IndexedSeq[IRI] =
		val q = s"""
			|SELECT DISTINCT ?s WHERE {
			|  ?s a ?t .
			|  FILTER(?t IN (<${metaVocab.dataObjectClass}>, <${metaVocab.docObjectClass}>, <${metaVocab.collectionClass}>))
			|}""".stripMargin
		val conn = repo.getConnection()
		try
			val result = conn.prepareTupleQuery(QueryLanguage.SPARQL, q).evaluate()
			val buf = ArrayBuffer.empty[IRI]
			try
				while result.hasNext() do
					result.next().getValue("s") match
						case iri: IRI => buf += iri
						case _ => ()
			finally result.close()
			buf.toIndexedSeq
		finally conn.close()

	private def writeInBatches(subjects: IndexedSeq[IRI]): Int =
		var totalWritten = 0
		subjects.grouped(WriteBatchSize).foreach: chunk =>
			val triples = chunk.flatMap(triplesFor)
			repo.transact: conn =>
				for t <- triples do conn.add(t, graphIri)
			totalWritten += triples.size
		totalWritten

	private def triplesFor(subj: IRI): IndexedSeq[Statement] =
		val out = ArrayBuffer.empty[Statement]

		val refs: Option[References] = citer.getReferences(subj)
		biblioLiteral(refs).foreach: lit =>
			out += factory.createStatement(subj, metaVocab.hasBiblioInfo, lit)

		val citationOpt = refs.fold(citer.getCitation(subj))(_.citationString)
		citationOpt.foreach: cit =>
			out += factory.createStatement(subj, metaVocab.hasCitationString, factory.createStringLiteral(cit))

		val licenceOpt = refs.fold(citer.getLicence(subj))(_.licence)
		licenceOpt.foreach: lic =>
			out += factory.createStatement(subj, metaVocab.dcterms.license, lic.url.toRdf)

		out.toIndexedSeq

	private def biblioLiteral(refs: Option[References]): Option[Value] =
		import spray.json.*
		import se.lu.nateko.cp.meta.core.data.JsonSupport.{given RootJsonFormat[References]}
		refs.map(js => factory.createStringLiteral(js.toJson.compactPrint))

end CitationMaterializer
