package se.lu.nateko.cp.meta.test.services.sparql.index

import org.eclipse.rdf4j.model.{IRI, Statement, Value}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
import se.lu.nateko.cp.meta.services.sparql.index.Keyword
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import org.eclipse.rdf4j.model.Resource
import org.roaringbitmap.buffer.MutableRoaringBitmap
import scala.util.Random
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.scalatest.AppendedClues.convertToClueful
import org.roaringbitmap.buffer.ImmutableRoaringBitmap

private val factory = SimpleValueFactory.getInstance()

class IndexDataTest extends AnyFunSpec {
	// Abusing ScalaTest a bit to allow top-level 'test' blocks as well as 'describe'-blocks.
	val testCase = it

	def test(name: String)(body: => Any) = {
		describe("") {
			it(name)(body)
		}
	}

	private val vocab = CpmetaVocab(factory)
	import vocab.{hasKeywords, hasName, hasObjectSpec, hasAssociatedProject}

	val seed = Math.abs(Random.nextInt())
	Random.setSeed(seed)
	info(s"Random seed: $seed")

	test("ObjEntry.fileName is cleared hasName statement is deleted") {
		val subject: IRI = factory.createIRI("https://meta.icos-cp.eu/objects/oAzNtfjXddcnG_irI8fJT7W6")
		val CpVocab.DataObject(hash, _) = subject: @unchecked
		val data = IndexData(1)()

		val statement = Rdf4jStatement(subject, hasName, factory.createLiteral("test name"))

		// This test does not rely on any existing statements
		given StatementSource = StaticStatementSource(Seq())

		// Insert hasName triple
		data.processUpdate(statement, true, vocab)
		assert(data.objs.length == 1)
		assert(data.getObjInfo(hash).fileName === Some("test name"))

		// Remove it
		data.processUpdate(statement, false, vocab)
		assert(data.getObjInfo(hash).fileName === None)
		assert(data.objs.length == 1)
	}

	test("Data object keywords indexing includes keywords from associated spec and project") {
		// TODO: Introduce test factory for generating RDF values
		// TODO: Generate random hash for objects
		// TODO: Generate random project names
		// TODO: Generate random spec names

		val dataObject: IRI = objectIRI("oAzNtfjXddcnG_irI8fJT7W6")
		val specProject: IRI = projectIRI("project")
		val spec = specIRI("spec")

		val otherDataObject: IRI = objectIRI("iS2ubFLw7HNbyKz_pHcJ89uL")
		val otherSpec = specIRI("other-spec")
		val otherSpecProject = specIRI("other-project")

		val data: IndexData = IndexData(20)()

		// There are 3 ways in which a data object can have keywords:
		// - Directly, through `hasKeywords`
		// - An associated spec
		// - A project associated with an associated spec

		// Insert objects in order, to get deterministic IDs.
		// Shuffle the rest of the statements.
		val statements =
			Seq(
				Rdf4jStatement(spec, RDF.TYPE, vocab.dataObjectSpecClass),
				Rdf4jStatement(otherSpec, RDF.TYPE, vocab.dataObjectSpecClass),
				Rdf4jStatement(dataObject, hasKeywords, factory.createLiteral("object keyword")),
				Rdf4jStatement(otherDataObject, hasKeywords, factory.createLiteral("other-object keyword"))
			)
				++ Random.shuffle(Seq(
					Rdf4jStatement(otherDataObject, hasKeywords, factory.createLiteral("other-object keyword")),
					Rdf4jStatement(dataObject, hasObjectSpec, spec),
					Rdf4jStatement(spec, hasAssociatedProject, specProject),
					Rdf4jStatement(spec, hasKeywords, factory.createLiteral("spec keyword")),
					Rdf4jStatement(specProject, hasKeywords, factory.createLiteral("project keyword")),
					//
					// Add an object->spec->project chain with keywords for another object
					Rdf4jStatement(otherDataObject, hasObjectSpec, otherSpec),
					Rdf4jStatement(otherSpec, hasKeywords, factory.createLiteral("other-spec keyword")),
					Rdf4jStatement(otherSpec, hasAssociatedProject, otherSpecProject),
					Rdf4jStatement(otherSpecProject, hasKeywords, factory.createLiteral("other-project-spec keyword"))
				))

		statements.foreach(statement =>
			given StatementSource = StaticStatementSource(statements)
			data.processUpdate(statement, true, vocab)
		)

		// For each keyword lookup, we expect the same bitmap
		// containing only the ID of the object, which will be 0.
		val objectBitmap = MutableRoaringBitmap.bitmapOf(0)
		val otherObjectBitmap = MutableRoaringBitmap.bitmapOf(1)

		val keywords = data.categoryKeys(Keyword).map(kw => (kw, data.categoryBitmap(Keyword, Seq(kw)))).toMap

		assert(keywords == Map(
			"object keyword" -> objectBitmap,
			"spec keyword" -> objectBitmap,
			"project keyword" -> objectBitmap,
			//
			// Check keywords for other object
			"other-object keyword" -> otherObjectBitmap,
			"other-spec keyword" -> otherObjectBitmap,
			"other-project-spec keyword" -> otherObjectBitmap
		))
	}

	describe("Data object keywords are kept up-to-date when") {
		def runStatements(statements: Seq[(Boolean, Rdf4jStatement)]): Map[String, ImmutableRoaringBitmap] = {
			val data = IndexData(100)()
			var store: Seq[Rdf4jStatement] = Seq()

			// Try processing before insertion sometimes, to make sure indexing does not rely
			// on the active triple being inserted already.
			val processBeforeInsertion = Random.nextBoolean()

			statements.foreach((isAssertion, statement) =>
				if (processBeforeInsertion) {
					given StatementSource = StaticStatementSource(store)
					data.processUpdate(statement, isAssertion, vocab)
				}

				store = if (isAssertion) {
					store :+ statement
				} else {
					store.filter(existing => existing != statement)
				}

				if (!processBeforeInsertion) {
					given StatementSource = StaticStatementSource(store)
					data.processUpdate(statement, isAssertion, vocab)
				}
			)

			data.categoryKeys(Keyword).flatMap(kw =>
				val bitmap = data.categoryBitmap(Keyword, Seq(kw))
				if (bitmap.isEmpty) {
					None
				} else {
					Some((kw, bitmap))
				}
			).toMap
		}

		def assertIndex(statements: Seq[(Boolean, Rdf4jStatement)], expectation: Map[String, ImmutableRoaringBitmap]) = {
			assert(runStatements(statements) == expectation) withClue s"\nStatements:\n${statements.mkString("\n")}"
		}

		val dataObject: IRI = objectIRI("oAzNtfjXddcnG_irI8fJT7W6")
		val spec: IRI = projectIRI("spec")
		val project: IRI = projectIRI("project")

		// Set up object->spec->project chain
		val objSpecProj = Random.shuffle(Seq(
			(true, Rdf4jStatement(spec, RDF.TYPE, vocab.dataObjectSpecClass)),
			(true, Rdf4jStatement(dataObject, hasKeywords, factory.createLiteral("object keyword"))),
			(true, Rdf4jStatement(dataObject, hasObjectSpec, spec)),
			(true, Rdf4jStatement(spec, hasKeywords, factory.createLiteral("spec keyword"))),
			(true, Rdf4jStatement(spec, hasAssociatedProject, project)),
			(true, Rdf4jStatement(project, hasKeywords, factory.createLiteral("project keyword")))
		))

		val objectBitmap = MutableRoaringBitmap.bitmapOf(0)

		testCase("object keyword is added") {
			val addObjectKeyword = (true, Rdf4jStatement(dataObject, hasKeywords, factory.createLiteral("object edited")))

			// Try all permutations of the basic case here, so we don't have to do it in all other tests.
			objSpecProj.permutations.foreach(baseStatements => {
				val statements = baseStatements :+ addObjectKeyword
				assertIndex(
					statements,
					Map(
						"object keyword" -> objectBitmap,
						"object edited" -> objectBitmap,
						"spec keyword" -> objectBitmap,
						"project keyword" -> objectBitmap
					)
				)
			})
		}

		testCase("object keyword is removed") {
			val removeObjectKeyword = (false, Rdf4jStatement(dataObject, hasKeywords, factory.createLiteral("object keyword")))

			assertIndex(
				objSpecProj :+ removeObjectKeyword,
				Map(
					"spec keyword" -> objectBitmap,
					"project keyword" -> objectBitmap
				)
			)
		}

		testCase("associated spec is edited") {
			val editSpec = Seq(
				(true, Rdf4jStatement(spec, hasKeywords, factory.createLiteral("spec edited,spec other edit"))),
				(false, Rdf4jStatement(spec, hasKeywords, factory.createLiteral("spec keyword")))
			)

			assertIndex(
				objSpecProj ++ editSpec,
				Map(
					"object keyword" -> objectBitmap,
					"spec edited" -> objectBitmap,
					"spec other edit" -> objectBitmap,
					"project keyword" -> objectBitmap
				)
			)
		}

		testCase("associated project is edited") {
			val editProject = Seq(
				(true, Rdf4jStatement(project, hasKeywords, factory.createLiteral("project edited,project other edit"))),
				(false, Rdf4jStatement(project, hasKeywords, factory.createLiteral("project keyword")))
			)

			assertIndex(
				objSpecProj ++ editProject,
				Map(
					"object keyword" -> objectBitmap,
					"spec keyword" -> objectBitmap,
					"project edited" -> objectBitmap,
					"project other edit" -> objectBitmap
				)
			)
		}

		testCase("associated project is removed") {
			val removeProject = (false, Rdf4jStatement(spec, hasAssociatedProject, project))
			assertIndex(
				objSpecProj :+ removeProject,
				Map(
					"object keyword" -> objectBitmap,
					"spec keyword" -> objectBitmap
				)
			)
		}

		testCase("another project is added") {
			val otherProject = projectIRI("other project")
			val addOtherProject =
				Seq(
					(true, Rdf4jStatement(otherProject, hasKeywords, factory.createLiteral("other project keyword"))),
					(true, Rdf4jStatement(spec, hasAssociatedProject, otherProject))
				)

			assertIndex(
				(objSpecProj) ++ addOtherProject,
				Map(
					"object keyword" -> objectBitmap,
					"spec keyword" -> objectBitmap,
					"project keyword" -> objectBitmap,
					"other project keyword" -> objectBitmap
				)
			)
		}

		testCase("associated spec is removed") {
			val removeSpec = (false, Rdf4jStatement(dataObject, hasObjectSpec, spec))
			assertIndex(
				objSpecProj :+ removeSpec,
				Map(
					"object keyword" -> objectBitmap
				)
			)
		}

		testCase("spec with keywords overlapping data object is removed") {
			val statements = Random.shuffle(Seq(
				(true, Rdf4jStatement(dataObject, hasKeywords, factory.createLiteral("overlap"))),
				(true, Rdf4jStatement(dataObject, hasObjectSpec, spec)),
				(true, Rdf4jStatement(spec, hasKeywords, factory.createLiteral("overlap")))
			))
				:+ (false, Rdf4jStatement(dataObject, hasObjectSpec, spec))

			assertIndex(statements, Map("overlap" -> objectBitmap))
		}

		testCase("project with keywords overlapping data object is removed") {
			val statements = Random.shuffle(Seq(
				(true, Rdf4jStatement(dataObject, hasKeywords, factory.createLiteral("overlap"))),
				(true, Rdf4jStatement(dataObject, hasObjectSpec, spec)),
				(true, Rdf4jStatement(spec, hasAssociatedProject, project)),
				(true, Rdf4jStatement(spec, hasKeywords, factory.createLiteral("spec keyword"))),
				(true, Rdf4jStatement(project, hasKeywords, factory.createLiteral("overlap")))
			)) :+
				(false, Rdf4jStatement(spec, hasAssociatedProject, project))

			assertIndex(statements, Map("overlap" -> objectBitmap, "spec keyword" -> objectBitmap))
		}

		testCase("spec or project with overlapping keywords is removed") {
			val otherProject = projectIRI("other project")

			val statements = Random.shuffle(Seq(
				(true, Rdf4jStatement(dataObject, hasKeywords, factory.createLiteral("object keyword"))),
				(true, Rdf4jStatement(dataObject, hasObjectSpec, spec)),
				(true, Rdf4jStatement(spec, hasAssociatedProject, project)),
				(true, Rdf4jStatement(spec, hasKeywords, factory.createLiteral("overlap"))),
				(true, Rdf4jStatement(project, hasKeywords, factory.createLiteral("overlap"))),
				(true, Rdf4jStatement(otherProject, hasKeywords, factory.createLiteral("otherProject keyword"))),
				(true, Rdf4jStatement(spec, hasAssociatedProject, otherProject))
			))

			val removeProject = (false, Rdf4jStatement(spec, hasAssociatedProject, project))
			val removeSpec = (false, Rdf4jStatement(dataObject, hasObjectSpec, spec))

			assertIndex(
				statements :+ removeProject,
				Map(
					"object keyword" -> objectBitmap,
					"otherProject keyword" -> objectBitmap,
					"overlap" -> objectBitmap
				)
			)

			assertIndex(
				statements :+ removeSpec,
				Map(
					"object keyword" -> objectBitmap
				)
			)
		}

		testCase("overlapping keywords in spec is removed") {
			val statements = Random.shuffle(Seq(
				(true, Rdf4jStatement(dataObject, hasKeywords, factory.createLiteral("overlap"))),
				(true, Rdf4jStatement(dataObject, hasObjectSpec, spec)),
				(true, Rdf4jStatement(spec, hasKeywords, factory.createLiteral("overlap")))
			)) :+
				(false, Rdf4jStatement(spec, hasKeywords, factory.createLiteral("overlap")))

			assertIndex(
				statements,
				Map(
					"overlap" -> objectBitmap
				)
			)
		}

		testCase("overlapping keywords in project is removed") {
			val overlap = factory.createLiteral("overlap")

			val statements = Random.shuffle(Seq(
				(true, Rdf4jStatement(dataObject, hasObjectSpec, spec)),
				(true, Rdf4jStatement(spec, hasAssociatedProject, project)),
				(true, Rdf4jStatement(spec, hasKeywords, overlap)),
				(true, Rdf4jStatement(project, hasKeywords, overlap))
			)) :+
				(false, Rdf4jStatement(project, hasKeywords, overlap))

			assertIndex(statements, Map("overlap" -> objectBitmap))
		}

		testCase("spec with keywords is added before object association") {
			val statements = Seq(
				(true, Rdf4jStatement(spec, hasAssociatedProject, project)),
				(true, Rdf4jStatement(spec, hasKeywords, factory.createLiteral("spec keyword"))),
				(true, Rdf4jStatement(dataObject, hasObjectSpec, spec))
			)

			assertIndex(statements, Map("spec keyword" -> objectBitmap))
		}

		testCase("keywords are added to spec after last object was removed") {
			val statements = Seq(
				// Add spec and object
				(true, Rdf4jStatement(spec, RDF.TYPE, vocab.dataObjectSpecClass)),
				(true, Rdf4jStatement(dataObject, hasObjectSpec, spec)),
				// Remove the object, and add keyword to spec
				(false, Rdf4jStatement(dataObject, hasObjectSpec, spec)),
				(true, Rdf4jStatement(spec, hasKeywords, factory.createLiteral("spec keyword"))),
				// Add object again. It should receive the spec keyword
				(true, Rdf4jStatement(dataObject, hasObjectSpec, spec))
			)

			assertIndex(statements, Map("spec keyword" -> objectBitmap))
		}
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
			matchingStatement(Rdf4jStatement(subject, predicate, obj), st)
		).map(TestStatement(_))

		CloseableIterator.Wrap(filtered.iterator, () => ())
	}

	// Null arguments in getStatements means wildcard
	private def matching(query: Value, target: Value) = {
		query == null || query == target
	}

	// Null arguments in getStatements means wildcard
	private def matchingStatement(a: Rdf4jStatement, b: Rdf4jStatement) = {
		matching(a.subj, b.subj)
		&& matching(a.pred, b.pred)
		&& matching(a.obj, b.obj)
	}

	def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean = {
		statements.find(statement =>
			matchingStatement(Rdf4jStatement(subject, predicate, obj), statement)
		).isDefined
	}
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
