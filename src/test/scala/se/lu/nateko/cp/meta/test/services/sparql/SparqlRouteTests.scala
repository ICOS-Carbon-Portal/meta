package se.lu.nateko.cp.meta.test.services.sparql

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.CacheDirectives.*
import akka.http.scaladsl.model.headers.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.testkit.ScalatestRouteTest
import eu.icoscp.envri.Envri
import org.scalatest.DoNotDiscover
import org.scalatest.compatible.Assertion
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.routes.SparqlRoute
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.test.services.sparql.regression.TestDbFixture

import java.net.URI
import scala.concurrent.Await
import scala.concurrent.Future

import concurrent.duration.DurationInt

@DoNotDiscover
class SparqlRouteTests extends AsyncFunSpec with ScalatestRouteTest with TestDbFixture:

	import system.{log}

	val numberOfParallelQueries = 2
	val sparqlConfig = new SparqlServerConfig(5, 2, 2, numberOfParallelQueries, 0, 10, 8388608, Seq("test@nateko.lu.se"))
	given default(using system: ActorSystem): RouteTestTimeout = RouteTestTimeout(10.seconds)

	val sparqlRoute: Future[Route] =
		db.repo.map: repo =>
			val rdf4jServer = Rdf4jSparqlServer(repo, sparqlConfig, log)
			given ToResponseMarshaller[SparqlQuery] = rdf4jServer.marshaller
			given EnvriConfigs = Map(
				Envri.ICOS -> EnvriConfig(null, null, null, null, new URI("http://test.icos.eu/resources/"), null)
			)

			SparqlRoute.apply(sparqlConfig)

	def req(query: String, ip: String, additionalHeader: Option[HttpHeader] = None) =
		Post("/sparql", query).withHeaders(Accept(Rdf4jSparqlServer.csvSparql.mediaType), RawHeader("X-Forwarded-For", ip), additionalHeader.getOrElse(RawHeader("", "")))

	def testRoute(query: String, ip: String = "127.0.0.1", additionalHeader: Option[HttpHeader] = None)(test: => Assertion): Future[Assertion] =
		sparqlRoute map: route =>
			req(query, ip, additionalHeader) ~> route ~> check(test)

	describe("SparqlRoute"):
		it("Correct query should produce correct result and get cached"):
			val uri = "https://meta.icos-cp.eu/objects/R5U1rVcbEQbdf9l801lvDUSZ"
			val query = s"""select * where { 
						VALUES ?s { <$uri> }
						?s ?p ?o }"""

			testRoute(query):
				assert(status == StatusCodes.OK)
				assert(responseAs[String].contains(uri))
				assert(header("x-cache-status").get === RawHeader("X-Cache-Status", "MISS"))
			.flatMap: _ =>
				testRoute(query):
					assert(status == StatusCodes.OK)
					assert(header("x-cache-status").get == RawHeader("X-Cache-Status", "HIT"))

		it("Request with cache disabled should update cache"):
			val uri = "https://meta.icos-cp.eu/objects/R5U1rVcbEQbdf9l801lvDUSZ"
			val query = s"""select * where { 
						VALUES ?s { <$uri> }
						?s ?p ?o }"""

			testRoute(query, additionalHeader = Some(`Cache-Control`(`no-cache`))):
				assert(status == StatusCodes.OK)
				assert(responseAs[String].contains(uri))
				assert(header("x-cache-status").get === RawHeader("X-Cache-Status", "BYPASS"))
			.flatMap: _ =>
				testRoute(query):
					assert(status == StatusCodes.OK)
					assert(header("x-cache-status").fold(false)(_ == RawHeader("X-Cache-Status", "HIT")))

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

		val longRunningQuery = """
			prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
			select ?sumb2 where{
				?subm1 a cpmeta:DataSubmission .
				?sumb2 a cpmeta:DataSubmission .
				filter (?subm1 = ?sumb2)
			}
			order by ?subm1
			limit 3
		"""		

		it("Long running query should result in timeout"):
			testRoute(longRunningQuery):
				assert(status == StatusCodes.RequestTimeout)

		it("Exceeding SPARQL running quota results in Service Unavailable response to subsequent queries"):
			val uri = "https://meta.icos-cp.eu/objects/R5U1rVcbEQbdf9l801lvDUSZ"
			val ip = "127.0.1.1"

			val initRequests = Future.sequence(Seq(
				testRoute(longRunningQuery, ip):
					assert(status == StatusCodes.RequestTimeout), 
				testRoute(longRunningQuery, ip):
					assert(status == StatusCodes.RequestTimeout))
				)

			initRequests.flatMap: res =>
				testRoute(longRunningQuery, ip):
					assert(status == StatusCodes.ServiceUnavailable)

		it("Too many parallel queries"):
			val uri = "https://meta.icos-cp.eu/objects/R5U1rVcbEQbdf9l801lvDUSZ"
			def query(n: Int) = s"""select * where { <$uri> ?p ?o } # $n"""

			val ip = "127.0.2.1"

			sparqlRoute.flatMap: route =>
				def request(n: Int) = req(longRunningQuery, ip, Some(`Cache-Control`(`no-cache`)))
				route(request(1))
				route(request(2))
				Thread.sleep(100)
				testRoute(query(3), ip, Some(`Cache-Control`(`no-cache`))):
					assert(status == StatusCodes.RequestTimeout)

end SparqlRouteTests
