package se.lu.nateko.cp.meta.test.instanceserver

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer

class InstanceServerTests extends AnyFunSpec{

	val factory = SimpleValueFactory.getInstance()
	val ctxt = factory.createIRI("http://www.icos-cp.eu/ontology/")
	val ctxt2 = factory.createIRI("http://www.icos-cp.eu/ontology2/")

	def makeUri(suff: String) = factory.createIRI(ctxt.stringValue, suff)

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
