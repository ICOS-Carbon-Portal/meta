package se.lu.nateko.cp.meta.instanceserver.test

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.persistence.InMemoryRdfLog
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import org.openrdf.model.impl.ValueFactoryImpl
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer
import se.lu.nateko.cp.meta.utils.SesameUtils

class InstanceServerTests extends FunSpec{

	import scala.concurrent.ExecutionContext.Implicits.global
	import SesameUtils._

	val factory = new ValueFactoryImpl()
	val ctxt = factory.createURI("http://www.icos-cp.eu/ontology/")

	def makeUri(suff: String) = factory.createURI(ctxt.stringValue, suff)

	describe("LoggingInstanceServer over SesameInstanceServer"){

		describe("after initializing with an empty in-memory log"){

			val log = new InMemoryRdfLog()

			val sesameRepoFuture = RdfUpdateLogIngester.ingest(log.updates, ctxt)
			val sesameRepo = Await.result(sesameRepoFuture, Duration.Inf)

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
				val viaScala = repRes.asScalaIterator.toArray
			}
		}

	}

	describe("SesameInstanceServer"){
		describe("makeNewInstance"){
			it("makes a correct URI if prefix ends with '/'"){
				pending
			}

			it("makes a correct URI if prefix does not end with '/'"){
				pending
			}
		}
	}

}