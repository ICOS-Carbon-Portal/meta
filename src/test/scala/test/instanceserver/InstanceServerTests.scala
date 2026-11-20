package se.lu.nateko.cp.meta.test.instanceserver

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.instanceserver.{LoggingInstanceServer, Rdf4jInstanceServer}
import se.lu.nateko.cp.meta.persistence.{InMemoryRdfLog, RdfUpdateLogIngester}
import se.lu.nateko.cp.meta.utils.rdf4j.*

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

		describe("when asked to write an IRI that SPARQL cannot express"){
			val log = new InMemoryRdfLog()
			val rdf4jRepo = SailRepository(MemoryStore())
			rdf4jRepo.init()
			val loggingServer = new LoggingInstanceServer(new Rdf4jInstanceServer(rdf4jRepo, ctxt), log)

			val res = loggingServer.addInstance(factory.createIRI("http://meta.icos-cp.eu/resources/fundings/107_Grant agreement"), makeUri("Funding"))

			it("fails without logging the update, so that the RDF log cannot replay it into another store"){
				assert(res.isFailure)
				assert(log.updates.toSeq.isEmpty)
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

		describe("writing IRIs that SPARQL cannot express"){
			val repo = new SailRepository(new MemoryStore)
			repo.init()
			val server = new Rdf4jInstanceServer(repo, ctxt)

			def stored = server.getStatements(None, None, None).toIndexedSeq
			val good = factory.createStatement(makeUri("inst1"), RDF.TYPE, makeUri("class1"))
			// the shape of IRI that a decoded UriId produces
			val withSpace = factory.createIRI("http://meta.icos-cp.eu/resources/people/Paul_Di Tommasi")
			val badSubject = factory.createStatement(withSpace, RDF.TYPE, makeUri("class1"))
			val badObject = factory.createStatement(makeUri("inst1"), RDF.TYPE, factory.createIRI(" http://example.org/x"))

			it("rejects the whole batch if any assertion has such an IRI, and writes nothing"){
				val res = server.addAll(Seq(good, badSubject))
				assert(res.isFailure)
				assert(res.failed.get.getMessage.contains("<http://meta.icos-cp.eu/resources/people/Paul_Di Tommasi>"))
				assert(stored.isEmpty)
			}

			it("checks objects too"){
				assert(server.addAll(Seq(badObject)).isFailure)
				assert(stored.isEmpty)
			}

			it("accepts percent-encoded IRIs"){
				val encoded = factory.createIRI("http://meta.icos-cp.eu/resources/people/Paul_Di%20Tommasi")
				assert(server.add(factory.createStatement(encoded, RDF.TYPE, makeUri("class1"))).isSuccess)
			}

			it("still lets such statements be retracted, so that existing ones can be cleaned up"){
				// bypass the server, as if the statement had been stored before the check existed
				repo.transact(_.add(badSubject, ctxt)).get
				assert(server.remove(badSubject).isSuccess)
				assert(!stored.exists(_.getSubject == withSpace))
			}
		}
	}

}
