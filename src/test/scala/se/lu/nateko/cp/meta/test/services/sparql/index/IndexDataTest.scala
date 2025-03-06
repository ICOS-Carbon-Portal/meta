package se.lu.nateko.cp.meta.test.services.sparql.index

import org.eclipse.rdf4j.model.{IRI, Statement, Value}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
import se.lu.nateko.cp.meta.services.sparql.index.Keyword
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import org.eclipse.rdf4j.model.Resource
import org.roaringbitmap.buffer.MutableRoaringBitmap

private val factory = SimpleValueFactory.getInstance()

class IndexDataTest extends AnyFunSuite {
	private val vocab = CpmetaVocab(factory)
	import vocab.{hasKeywords, hasName, hasObjectSpec, hasAssociatedProject}

	test("ObjEntry.fileName is cleared when hasName statement is deleted") {
		val subject: IRI = factory.createIRI("https://meta.icos-cp.eu/objects/oAzNtfjXddcnG_irI8fJT7W6")
		val CpVocab.DataObject(hash, _) = subject: @unchecked
		val data = IndexData(1)()

		// IndexData requires a StatementSource but in this case we never pull any statements,
		// hence we can leave things unimplemented.

		val statement = Rdf4jStatement(subject, hasName, factory.createLiteral("test name"))

		// This test does not rely on any existing statements
		given StatementSource = StaticStatementSource(Seq())

		// Insert hasName triple
		data.processUpdate(statement, true, vocab)
		assert(data.getObjEntry(hash).fileName === Some("test name"))

		// Remove it
		data.processUpdate(statement, false, vocab)
		assert(data.getObjEntry(hash).fileName === None)
		assert(data.objs.length == 1)
	}

	// TODO: Test editing of keywords of associated specs and project
	// TODO: Test addition/removal of associated specs and project
	test("Data object keywords are indexed from associated spec and project") {
		// TODO: Introduce test factory for generating RDF values
		// TODO: Generate random hash for objects
		// TODO: Generate random project names
		// TODO: Generate random spec names

		val dataObject: IRI = objectIRI("oAzNtfjXddcnG_irI8fJT7W6")
		val objectProject: IRI = projectIRI("project")
		val specProject: IRI = projectIRI("spec-project")
		val spec = specIRI("spec")

		val otherDataObject: IRI = objectIRI("iS2ubFLw7HNbyKz_pHcJ89uL")
		val otherSpec = specIRI("other-spec")
		val otherSpecProject = specIRI("other-spec-project")

		val data: IndexData = IndexData(20)()

		// There are 3 ways in which a data object can have associated keywords:
		// - An associated spec
		// - A project associated with an associated spec
		// - A directly associated project
		val statements = Seq(
			Rdf4jStatement(dataObject, hasKeywords, factory.createLiteral("object keyword")),
			Rdf4jStatement(dataObject, hasObjectSpec, spec),
			Rdf4jStatement(dataObject, hasAssociatedProject, objectProject),
			Rdf4jStatement(spec, hasAssociatedProject, specProject),
			Rdf4jStatement(spec, hasKeywords, factory.createLiteral("spec keyword")),
			Rdf4jStatement(specProject, hasKeywords, factory.createLiteral("spec-project keyword")),
			Rdf4jStatement(objectProject, hasKeywords, factory.createLiteral("object-project keyword")),
			//
			// Add an object->spec->project chain with keywords for another object
			Rdf4jStatement(otherDataObject, hasKeywords, factory.createLiteral("other-object keyword")),
			Rdf4jStatement(otherDataObject, hasObjectSpec, otherSpec),
			Rdf4jStatement(otherSpec, hasKeywords, factory.createLiteral("other-spec keyword")),
			Rdf4jStatement(otherSpec, hasAssociatedProject, otherSpecProject),
			Rdf4jStatement(otherSpecProject, hasKeywords, factory.createLiteral("other-project-spec keyword"))
		)

		statements.foreach(statement =>
			given StatementSource = StaticStatementSource(statements)
			data.processUpdate(statement, true, vocab)
		)

		// For each keyword lookup, we expect the same bitmap
		// containing only the ID of the object, which will be 0.
		val objectBitmap = MutableRoaringBitmap.bitmapOf(0)
		val otherObjectBitmap = MutableRoaringBitmap.bitmapOf(1)

		val keywords = data.categMap(Keyword)
		assert(keywords == Map(
			"object keyword" -> objectBitmap,
			"spec keyword" -> objectBitmap,
			"spec-project keyword" -> objectBitmap,
			"object-project keyword" -> objectBitmap,
			//
			// Check keywords for other object
			"other-object keyword" -> otherObjectBitmap,
			"other-spec keyword" -> otherObjectBitmap,
			"other-project-spec keyword" -> otherObjectBitmap
		))
	}
}

private def objectIRI(hash: String) = {
	factory.createIRI(s"https://meta.icos-cp.eu/objects/$hash")
}

private def projectIRI(name: String) = {
	factory.createIRI(s"http://meta.icos-cp.eu/resources/projects/$name")
}

private def specIRI(name: String) = {
	factory.createIRI(s"http://meta.icos-cp.eu/resources/cpmeta/$name")
}

class StaticStatementSource(statements: Seq[Rdf4jStatement]) extends StatementSource {
	def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] = {
		val filtered = statements.filter(st =>
			matching(subject, st.subj)
				&& matching(predicate, st.pred)
				&& matching(obj, st.obj)
		).map(TestStatement(_))

		CloseableIterator.Wrap(filtered.iterator, () => ())
	}

	// Null arguments in getStatements means wildcard
	private def matching(query: Value, target: Value) = {
		query == null || query == target
	}

	// Leave unimplemented until used by any test
	def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean = ???
}

// The StatementSource interface requires the more general Statement type, which includes context.
// IndexData never cares about context, however, so we can "upcast" like this.
// TODO: Introduce a more limited RdfStatementSource, use it in IndexData, and get rid of this workaround.
final class TestStatement(inner: Rdf4jStatement) extends Statement {
	override def getObject(): Value = inner.obj
	override def getSubject(): Resource = inner.subj
	override def getPredicate(): IRI = inner.pred
	override def getContext(): Resource = ???
}
