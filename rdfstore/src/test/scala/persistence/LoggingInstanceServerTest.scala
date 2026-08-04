package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.utils.rdf4j.accessEagerly

class LoggingInstanceServerTest extends AnyFunSpec:
	private val factory = SimpleValueFactory.getInstance()
	private val context = factory.createIRI("https://example.org/graph")
	private val statement = factory.createStatement(
		factory.createIRI("https://example.org/subject"),
		factory.createIRI("https://example.org/predicate"),
		factory.createLiteral("value")
	)

	describe("RdfLogManager instance updates"):
		it("uses the original LoggingInstanceServer behavior for configured graphs"):
			val repo = SailRepository(MemoryStore())
			repo.init()
			val log = InMemoryRdfLog()
			val manager = RdfLogManager.fromBindings(Seq(RdfLogManager.Binding("test", context, log)))

			manager.applyAll(repo, context, Seq(RdfUpdate(statement, true))).get

			assert(log.updates.toSeq == Seq(RdfUpdate(statement, true)))
			assert(repo.accessEagerly(_.hasStatement(statement, false, context)))
			repo.shutDown()
