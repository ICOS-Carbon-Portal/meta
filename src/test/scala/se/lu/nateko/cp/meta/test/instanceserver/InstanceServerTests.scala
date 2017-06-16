package se.lu.nateko.cp.meta.test.instanceserver

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.persistence.InMemoryRdfLog
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer
import se.lu.nateko.cp.meta.utils.sesame._
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.eclipse.rdf4j.model.vocabulary.RDF

class InstanceServerTests extends FunSpec{

	import scala.concurrent.ExecutionContext.Implicits.global

	val factory = SimpleValueFactory.getInstance()
	val ctxt = factory.createIRI("http://www.icos-cp.eu/ontology/")
	val ctxt2 = factory.createIRI("http://www.icos-cp.eu/ontology2/")

	def makeUri(suff: String) = factory.createIRI(ctxt.stringValue, suff)

	describe("LoggingInstanceServer over SesameInstanceServer"){

		describe("after initializing with an empty in-memory log"){

			val log = new InMemoryRdfLog()

			val sesameRepo = RdfUpdateLogIngester.ingest(log.updates, ctxt)

			val innerInstServer = new SesameInstanceServer(sesameRepo, ctxt)
			val loggingServer = new LoggingInstanceServer(innerInstServer, log)

			val person = makeUri("Person")
			val hasName = makeUri("hasName")

			val person1 = loggingServer.makeNewInstance(person)
			val person2 = loggingServer.makeNewInstance(person)
			loggingServer.addInstance(person1, person)
			loggingServer.addInstance(person2, person)
			loggingServer.addPropertyValue(person1, hasName, factory.createLiteral("John"))
			loggingServer.addPropertyValue(person2, hasName, factory.createLiteral("Jane"))
			loggingServer.removeInstance(person1)

			it("logs all the RDF updates properly"){
				val updates = log.updates.toSeq
				assert(updates.map(_.isAssertion) === Seq(true, true, true, true, false, false))
			}

			it("updates the underlying Sesame repository correctly"){
				val allStatements = sesameRepo.access(conn => conn.getStatements(null, null, null, false, ctxt)).toIndexedSeq
				assert(allStatements.size === 2)
			}

			it("SesameUtils RepositoryResult to Iterator conversion"){
				val conn = sesameRepo.getConnection
				def repRes = conn.getStatements(null, null, null, false, ctxt)
				repRes.asScalaIterator.toArray
			}
		}

	}

	describe("SesameInstanceServer"){
		describe("makeNewInstance"){

			val repo = new SailRepository(new MemoryStore)
			repo.initialize()
			val server = new SesameInstanceServer(repo, ctxt)

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
			repo.initialize()
			val server1 = new SesameInstanceServer(repo, ctxt)
			val server2 = new SesameInstanceServer(repo, ctxt2)
			
			server1.addInstance(makeUri("inst1"), makeUri("class1"))
			server2.addInstance(makeUri("inst2"), makeUri("class2"))

			it("Reads all the triples written with different contexts"){
				val server = new SesameInstanceServer(repo)
				val statements = server.getStatements(None, None, None).toIndexedSeq
				assert(statements.size === 2)
			}
			
			it("Finds an exact triple"){
				val server = new SesameInstanceServer(repo, Nil, Seq(ctxt))
				val statements = server.getStatements(Some(makeUri("inst1")), Some(RDF.TYPE), Some(makeUri("class1"))).toIndexedSeq
				assert(statements.size === 1)
			}
		}
	}

}