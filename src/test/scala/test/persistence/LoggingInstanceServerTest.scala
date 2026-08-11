package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.instanceserver.{LoggingInstanceServer, Rdf4jInstanceServer}
import se.lu.nateko.cp.meta.utils.rdf4j.*

class LoggingInstanceServerTest extends AnyFunSpec:

	val factory = SimpleValueFactory.getInstance()
	val ctxt = factory.createIRI("http://www.icos-cp.eu/ontology/")

	def makeUri(suff: String) = factory.createIRI(ctxt.stringValue, suff)

	describe("LoggingInstanceServer over Rdf4jInstanceServer"){

		describe("after initializing with an empty in-memory log"){

			val log = new InMemoryRdfLog()

			val rdf4jRepo = SailRepository(MemoryStore())
			rdf4jRepo.init()

			val innerInstServer = new Rdf4jInstanceServer(rdf4jRepo, ctxt)
			val loggingServer = new LoggingInstanceServer(innerInstServer, log)

			val person = makeUri("Person")
			val hasName = makeUri("hasName")

			val person1 = loggingServer.makeNewInstance(person)
			val person2 = loggingServer.makeNewInstance(person)
			loggingServer.addInstance(person1, person)
			loggingServer.addInstance(person2, person)
			loggingServer.addPropertyValue(person1, hasName, factory.createLiteral("John"))
			loggingServer.addPropertyValue(person2, hasName, factory.createLiteral("Jane"))

			loggingServer.removeAll:
				loggingServer.access: conn ?=>
					conn.getStatements(person1, null, null).toIndexedSeq

			it("logs all the RDF updates properly"){
				val updates = log.updates.toSeq
				assert(updates.map(_.isAssertion) === Seq(true, true, true, true, false, false))
			}

			it("updates the underlying Rdf4j repository correctly"){
				val allStatements = rdf4jRepo.access(conn => conn.getStatements(null, null, null, false, ctxt)).toIndexedSeq
				assert(allStatements.size === 2)
			}

			it("Rdf4jUtils RepositoryResult to Iterator conversion"){
				val conn = rdf4jRepo.getConnection
				def repRes = conn.getStatements(null, null, null, false, ctxt)
				repRes.asPlainScalaIterator.toArray
			}
		}

	}
