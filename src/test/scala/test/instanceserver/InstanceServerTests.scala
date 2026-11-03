package se.lu.nateko.cp.meta.test.instanceserver

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Resource, Value}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.{LoggingInstanceServer, Rdf4jInstanceServer, RdfUpdate}
import se.lu.nateko.cp.meta.persistence.{InMemoryRdfLog, RdfUpdateLog, RdfUpdateLogIngester}
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.time.Instant
import scala.collection.mutable.ArrayBuffer

class InstanceServerTests extends AnyFunSpec{

	val factory = SimpleValueFactory.getInstance()
	val ctxt = factory.createIRI("http://www.icos-cp.eu/ontology/")
	val ctxt2 = factory.createIRI("http://www.icos-cp.eu/ontology2/")

	def makeUri(suff: String) = factory.createIRI(ctxt.stringValue, suff)

	describe("LoggingInstanceServer over Rdf4jInstanceServer"){

		describe("after initializing with an empty in-memory log"){

			val log = new InMemoryRdfLog()

			val rdf4jRepo = RdfUpdateLogIngester.ingestIntoMemory(log.updates, ctxt)

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

		describe("when the RDF log fails to register the updates"){

			val logError = new RuntimeException("RDF log failure")

			// RDF log that can be made to fail on demand; records every batch it was asked to append
			class FlakyRdfLog extends RdfUpdateLog{
				var failing = false
				val attempted = ArrayBuffer.empty[Seq[RdfUpdate]]
				private val accepted = new InMemoryRdfLog()

				def appendAll(updates: IterableOnce[RdfUpdate]): Unit = {
					val batch = updates.iterator.toIndexedSeq
					attempted += batch
					if(failing) throw logError
					accepted.appendAll(batch)
				}
				def updates: CloseableIterator[RdfUpdate] = accepted.updates
				def timedUpdates: CloseableIterator[(Instant, RdfUpdate)] = CloseableIterator.empty
				def close(): Unit = {}
			}

			def triples(repo: Repository): Set[(Resource, IRI, Value)] =
				repo.access(conn => conn.getStatements(null, null, null, false))
					.map(st => (st.getSubject, st.getPredicate, st.getObject)).toSet

			it("rolls back all triples of a failed insert, keeping the repository consistent with the log"){
				val repo = new SailRepository(new MemoryStore)
				repo.init()
				val log = new FlakyRdfLog
				val server = new LoggingInstanceServer(new Rdf4jInstanceServer(repo, ctxt), log)

				val existing = factory.createStatement(makeUri("inst0"), RDF.TYPE, makeUri("class0"))
				val batch = Seq(
					factory.createStatement(makeUri("inst1"), RDF.TYPE, makeUri("class1")),
					factory.createStatement(makeUri("inst1"), makeUri("hasName"), factory.createLiteral("John")),
					factory.createStatement(makeUri("inst2"), RDF.TYPE, makeUri("class1"))
				)
				assert(server.add(existing).isSuccess)

				log.failing = true
				val result = server.addAll(batch)

				assert(result.failed.toOption === Some(logError))
				// the log was reached, i.e. the triples had been staged in the transaction before it failed
				assert(log.attempted.last === batch.map(RdfUpdate(_, true)))
				assert(triples(repo) === Set((existing.getSubject, existing.getPredicate, existing.getObject)))
				assert(triples(repo) === triples(RdfUpdateLogIngester.ingestIntoMemory(log.updates, ctxt)))
			}
		}

	}

	describe("Rdf4jInstanceServer"){
		describe("makeNewInstance"){

			val repo = new SailRepository(new MemoryStore)
			repo.init()
			val server = new Rdf4jInstanceServer(repo, ctxt)

			it("makes a correct URI if prefix ends with '/'"){
				val uri = server.makeNewInstance(ctxt)
				assert(!uri.stringValue.contains("/ontology//"))
			}

			it("makes a correct URI if prefix does not end with '/'"){
				val prefix = makeUri("MyClassName")
				val uri = server.makeNewInstance(prefix)
				assert(uri.stringValue.contains("/MyClassName/"))
			}
		}

		describe("Reading with global context"){
			val repo = new SailRepository(new MemoryStore)
			repo.init()
			val server1 = new Rdf4jInstanceServer(repo, ctxt)
			val server2 = new Rdf4jInstanceServer(repo, ctxt2)
			
			server1.addInstance(makeUri("inst1"), makeUri("class1"))
			server2.addInstance(makeUri("inst2"), makeUri("class2"))

			it("Reads all the triples written with different contexts"){
				val server = new Rdf4jInstanceServer(repo)
				val statements = server.getStatements(None, None, None).toIndexedSeq
				assert(statements.size === 2)
			}
			
			it("Finds an exact triple"){
				val server = new Rdf4jInstanceServer(repo, Nil, ctxt)
				val statements = server.getStatements(Some(makeUri("inst1")), Some(RDF.TYPE), Some(makeUri("class1"))).toIndexedSeq
				assert(statements.size === 1)
			}
		}
	}

}
