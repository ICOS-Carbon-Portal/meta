package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.utils.rdf4j.*

import scala.collection.mutable
import scala.util.Try

enum ReconcileMode {
	case MissingOnly
	case Structural
	case Full
	case FullWithDataCiteRefresh

	def reconcilesExisting: Boolean = this != MissingOnly
	def removesStale: Boolean = this == Full || this == FullWithDataCiteRefresh
	def refreshesDataCite: Boolean = this == FullWithDataCiteRefresh
}

final case class DesiredCitation(subject: IRI, triples: IndexedSeq[Statement])

final case class ExistingCitation(
	triples: IndexedSeq[Statement],
	references: Option[References],
	citationString: Option[String]
)

final case class ReconcileStats(
	added: Int = 0,
	updated: Int = 0,
	unchanged: Int = 0,
	removed: Int = 0,
	mutatedTriples: Int = 0
) {
	def +(other: ReconcileStats): ReconcileStats = ReconcileStats(
		added + other.added,
		updated + other.updated,
		unchanged + other.unchanged,
		removed + other.removed,
		mutatedTriples + other.mutatedTriples
	)
}

/**
 * Reconciles complete, per-subject citation snapshots in the derived graph.
 *
 * Comparisons deliberately ignore statement context. Changed subjects are
 * removed and replaced in one transaction, so readers never see a partial
 * citation and obsolete predicates cannot survive an update.
 */
final class CitationReconciler(
	repo: Repository,
	graphIri: IRI,
	biblioPredicate: IRI,
	citationPredicate: IRI,
	batchSize: Int = 1000
) {
	private given ValueFactory = repo.getValueFactory

	def loadExisting(subjects: Seq[IRI]): Map[IRI, ExistingCitation] = {
		if (subjects.isEmpty) Map.empty
		else {
			val values = subjects.iterator.map(iri => s"<${iri.stringValue}>").mkString(" ")
			val query =
				s"""SELECT ?s ?p ?o WHERE {
				   |  GRAPH <${graphIri.stringValue}> {
				   |    VALUES ?s { $values }
				   |    ?s ?p ?o
				   |  }
				   |}""".stripMargin
			val grouped = mutable.LinkedHashMap.empty[IRI, mutable.ArrayBuffer[Statement]]
			repo.accessEagerly { conn =>
				val result = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
				try {
					while (result.hasNext()) {
						val row = result.next()
						(row.getValue("s"), row.getValue("p"), row.getValue("o")) match {
							case (subject: IRI, predicate: IRI, obj) =>
								grouped.getOrElseUpdate(subject, mutable.ArrayBuffer.empty) +=
									summon[ValueFactory].createStatement(subject, predicate, obj)
							case _ => ()
						}
					}
				}
				finally result.close()
			}

			grouped.iterator.map { (subject, statements) =>
				val triples = statements.toIndexedSeq
				val refs = triples.iterator
					.filter(_.getPredicate == biblioPredicate)
					.flatMap(statement => parseReferences(statement.getObject.stringValue))
					.nextOption()
				val citation = triples.iterator
					.find(_.getPredicate == citationPredicate)
					.map(_.getObject.stringValue)
				subject -> ExistingCitation(triples, refs, citation)
			}.toMap
		}
	}

	def reconcile(desired: Seq[DesiredCitation]): ReconcileStats = {
		if (desired.isEmpty) ReconcileStats()
		else {
			val existing = loadExisting(desired.map(_.subject))
			val changed = desired.filter { item =>
				normalize(item.triples) != normalize(existing.get(item.subject).toSeq.flatMap(_.triples))
			}

			if (changed.nonEmpty) {
				repo.transact { conn =>
					for (item <- changed) {
						conn.remove(item.subject, null, null, graphIri)
						for (triple <- item.triples) conn.add(triple, graphIri)
					}
				}.get
			}

			desired.foldLeft(ReconcileStats()) { (stats, item) =>
				val before = existing.get(item.subject).fold(IndexedSeq.empty)(_.triples)
				if (normalize(before) == normalize(item.triples)) {
					stats.copy(unchanged = stats.unchanged + 1)
				}
				else if (item.triples.isEmpty) {
					stats.copy(
						removed = stats.removed + Option.when(before.nonEmpty)(1).getOrElse(0),
						mutatedTriples = stats.mutatedTriples + before.size
					)
				}
				else if (before.isEmpty) {
					stats.copy(added = stats.added + 1, mutatedTriples = stats.mutatedTriples + item.triples.size)
				}
				else {
					stats.copy(
						updated = stats.updated + 1,
						mutatedTriples = stats.mutatedTriples + before.size + item.triples.size
					)
				}
			}
		}
	}

	def remove(subjects: Seq[IRI]): ReconcileStats = {
		subjects.grouped(batchSize).foldLeft(ReconcileStats()) { (total, batch) =>
			val existing = loadExisting(batch)
			if (existing.nonEmpty) {
				repo.transact { conn =>
					for (subject <- existing.keys) conn.remove(subject, null, null, graphIri)
				}.get
			}
			total + ReconcileStats(
				removed = existing.size,
				mutatedTriples = existing.valuesIterator.map(_.triples.size).sum
			)
		}
	}

	private def normalize(triples: Iterable[Statement]): Set[(IRI, Value)] =
		triples.iterator.map(statement => statement.getPredicate -> statement.getObject).toSet

	private def parseReferences(json: String): Option[References] = {
		import spray.json.*
		import se.lu.nateko.cp.meta.core.data.JsonSupport.{given RootJsonFormat[References]}
		Try(json.parseJson.convertTo[References]).toOption
	}
}
