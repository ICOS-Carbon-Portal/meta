package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.sparql.magic.RdfLoggingSail

class RdfLoggingSailTest extends AnyFunSpec:
	private val factory = SimpleValueFactory.getInstance()
	private val context = factory.createIRI("https://example.org/graph")
	private val subject = factory.createIRI("https://example.org/subject")
	private val predicate = factory.createIRI("https://example.org/predicate")
	private val value = factory.createLiteral("value")

	describe("RdfLoggingSail"):
		it("logs committed graph changes but not rolled-back changes or startup replay"):
			val log = InMemoryRdfLog()
			val manager = RdfLogManager.fromBindings(Seq(RdfLogManager.Binding("test", context, log)))
			val sail = RdfLoggingSail(MemoryStore(), manager)
			val repo = SailRepository(sail)
			repo.init()

			val startupConnection = repo.getConnection
			startupConnection.begin()
			startupConnection.add(subject, predicate, value, context)
			startupConnection.commit()
			startupConnection.close()
			assert(log.updates.toSeq.isEmpty)

			sail.enableRecording()
			val connection = repo.getConnection
			connection.begin()
			connection.remove(subject, predicate, value, context)
			connection.commit()

			connection.begin()
			connection.add(subject, predicate, value, context)
			connection.rollback()
			connection.close()

			val updates = log.updates.toSeq
			assert(updates.size == 1)
			assert(!updates.head.isAssertion)
			assert(updates.head.statement == factory.createStatement(subject, predicate, value))
			repo.shutDown()
