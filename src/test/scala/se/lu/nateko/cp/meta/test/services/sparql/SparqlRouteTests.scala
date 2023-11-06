package se.lu.nateko.cp.meta.test.services.sparql

import akka.http.scaladsl.testkit.ScalatestRouteTest
import se.lu.nateko.cp.meta.routes.SparqlRoute
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import se.lu.nateko.cp.meta.api.SparqlQuery
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.test.services.sparql.regression.TestDb
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import se.lu.nateko.cp.meta.SparqlServerConfig
import spray.json.*
import scala.concurrent.Await
import concurrent.duration.DurationInt
import eu.icoscp.envri.Envri
import java.net.URI
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes
import org.scalatest.funspec.AsyncFunSpec
import scala.concurrent.Future
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.RequestEntity
import org.scalatest.BeforeAndAfterAll
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.headers.Accept
import org.scalatest.compatible.Assertion
import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.model.headers.`X-Forwarded-For`
import akka.http.scaladsl.model.RemoteAddress
import java.net.SocketAddress
import java.net.InetAddress
import akka.http.scaladsl.model.headers.RawHeader
import scala.util.Success
import scala.util.Failure
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.server.directives.CachingDirectives.*
import akka.http.scaladsl.model.headers.*
import se.lu.nateko.cp.meta.test.services.sparql.regression.TestDbFixture



class SparqlRouteTests extends AsyncFunSpec with ScalatestRouteTest with BeforeAndAfterAll with TestDbFixture {

	import system.{log}

	// override protected def afterAll(): Unit =
	// 	db.cleanup()

	val sparqlConfig = new SparqlServerConfig(5, 2, 2, 2, 100, 10, 8388608, Seq("test@nateko.lu.se"))
	given default(using system: ActorSystem): RouteTestTimeout = RouteTestTimeout(10.seconds)

	val sparqlRoute: Future[Route] =
		db.repo.map: repo =>
			val rdf4jServer = Rdf4jSparqlServer(repo, sparqlConfig, log)
			given ToResponseMarshaller[SparqlQuery] = rdf4jServer.marshaller
			given EnvriConfigs = Map(
				Envri.ICOS -> EnvriConfig(null, null, null, null, new URI("http://test.icos.eu/resources/"), null)
			)

			SparqlRoute.apply(sparqlConfig)

	def testRoute(query: String, additionalHeader: Option[HttpHeader] = None)(test: => Assertion): Future[Assertion] =
		sparqlRoute map: route =>
			Post("/sparql", query).withHeaders(Accept(Rdf4jSparqlServer.csvSparql.mediaType), RawHeader("X-Forwarded-For", "127.0.5.1"), additionalHeader.getOrElse(RawHeader("", ""))) ~> route ~> check(test)


	describe("SparqlRoute"):
		it("Correct query should produce correct result"):
			val uri = "https://meta.icos-cp.eu/objects/R5U1rVcbEQbdf9l801lvDUSZ"
			val query = s"""select * where { 
						VALUES ?s { <$uri> }
						?s ?p ?o }"""

			testRoute(query):
				assert(status == StatusCodes.OK)
				assert(responseAs[String].contains(uri))


		it("Syntax error in query"):
			val query = "selecct * where { ?s ?p ?o }"
			
			testRoute(query):
				assert(status == StatusCodes.BadRequest)
				assert(responseAs[String].contains("selecct"))

		it("Error in prologue"):
			val objUri = "https://meta.icos-cp.eu/objects/a31A8q-hCILq74TM9GoIW9Yg"
			val query = s"""
				prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>

				select ?dobj ?cit where {
					VALUES ?dobj { <$objUri> }
					?dobj cpmeta:hasBiblioInfo ?cit .
				}
				"""

			testRoute(query):
				assert(status == StatusCodes.InternalServerError)
				assert(responseAs[String].contains("org.eclipse.rdf4j.sail.SailException: head of empty String"))

		it("Error later in response"):
			val objUri = "https://meta.icos-cp.eu/objects/a31A8q-hCILq74TM9GoIW9Yg"
			val query = s"""
				prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>

				select ?dobj ?cit where {
					{
						select ?dobj ?cit where {
							?dobj cpmeta:hasObjectSpec ?specUri .
							?dobj cpmeta:hasBiblioInfo ?cit .
						}
						limit 50
					}
					UNION
					{
						select ?dobj ?cit where {
							VALUES ?dobj { <$objUri> }
							?dobj cpmeta:hasBiblioInfo ?cit .
						}
					}
				}
				"""

			testRoute(query):
				assert(status == StatusCodes.OK)
				assert(responseAs[String].contains("org.eclipse.rdf4j.sail.SailException: head of empty String"))
	
		it("Timeout"):
			val query = """
				prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
				select ?sumb2 where{
					?subm1 a cpmeta:DataSubmission .
					?sumb2 a cpmeta:DataSubmission .
					filter (?subm1 = ?sumb2)
				}
				order by ?subm1
				limit 3
			"""

			testRoute(query):
				assert(status == StatusCodes.RequestTimeout)
}
