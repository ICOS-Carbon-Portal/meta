package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Statement}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.data.{JsonSupport, References}

import spray.json.*

class CitationReconcilerTest extends AnyFunSpec with BeforeAndAfterAll {
	private val repo = SailRepository(MemoryStore())
	repo.init()
	private val factory = repo.getValueFactory
	private val graph = iri("graph")
	private val biblio = iri("hasBiblio")
	private val citation = iri("hasCitation")
	private val other = iri("other")
	private val reconciler = CitationReconciler(repo, graph, biblio, citation)

	override def afterAll(): Unit = repo.shutDown()

	private def iri(local: String): IRI = factory.createIRI(s"http://example.org/$local")
	private def statement(subject: IRI, predicate: IRI, value: String): Statement =
		factory.createStatement(subject, predicate, factory.createLiteral(value))

	private def stored(subject: IRI): Set[(IRI, String)] = {
		val conn = repo.getConnection()
		try {
			val result = conn.getStatements(subject, null, null, false, graph)
			try {
				val builder = Set.newBuilder[(IRI, String)]
				while (result.hasNext()) {
					val next = result.next()
					builder += next.getPredicate -> next.getObject.stringValue
				}
				builder.result()
			}
			finally result.close()
		}
		finally conn.close()
	}

	describe("CitationReconciler") {
		it("adds, leaves unchanged, and atomically replaces complete snapshots") {
			val subject = iri("object")
			val first = DesiredCitation(subject, IndexedSeq(statement(subject, citation, "old")))
			val added = reconciler.reconcile(Seq(first))
			assert(added.added === 1)
			assert(stored(subject) === Set(citation -> "old"))

			val unchanged = reconciler.reconcile(Seq(first))
			assert(unchanged.unchanged === 1)
			assert(unchanged.mutatedTriples === 0)

			val replacement = DesiredCitation(subject, IndexedSeq(
				statement(subject, citation, "new"),
				statement(subject, other, "new field")
			))
			val updated = reconciler.reconcile(Seq(replacement))
			assert(updated.updated === 1)
			assert(updated.mutatedTriples === 3)
			assert(stored(subject) === Set(citation -> "new", other -> "new field"))
		}

		it("removes an obsolete snapshot when the desired snapshot is empty") {
			val subject = iri("empty")
			reconciler.reconcile(Seq(DesiredCitation(
				subject, IndexedSeq(statement(subject, citation, "obsolete"))
			)))

			val removed = reconciler.reconcile(Seq(DesiredCitation(subject, IndexedSeq.empty)))
			assert(removed.removed === 1)
			assert(stored(subject).isEmpty)
		}

		it("removes stale subjects without touching other derived subjects") {
			val stale = iri("stale")
			val current = iri("current")
			reconciler.reconcile(Seq(
				DesiredCitation(stale, IndexedSeq(statement(stale, citation, "stale"))),
				DesiredCitation(current, IndexedSeq(statement(current, citation, "current")))
			))

			val removed = reconciler.remove(Seq(stale))
			assert(removed.removed === 1)
			assert(stored(stale).isEmpty)
			assert(stored(current) === Set(citation -> "current"))
		}

		it("loads stored bibliography and citation fields for structural refresh") {
			import JsonSupport.given
			val subject = iri("stored")
			val refs = References.empty.copy(citationString = Some("citation"))
			reconciler.reconcile(Seq(DesiredCitation(subject, IndexedSeq(
				statement(subject, biblio, refs.toJson.compactPrint),
				statement(subject, citation, "citation")
			))))

			val existing = reconciler.loadExisting(Seq(subject))(subject)
			assert(existing.references === Some(refs))
			assert(existing.citationString === Some("citation"))
		}
	}

	describe("ReconcileMode") {
		it("keeps DataCite refresh as a separate explicit mode") {
			assert(!ReconcileMode.Structural.refreshesDataCite)
			assert(ReconcileMode.Full.removesStale)
			assert(ReconcileMode.FullWithDataCiteRefresh.removesStale)
			assert(ReconcileMode.FullWithDataCiteRefresh.refreshesDataCite)
		}
	}
}
