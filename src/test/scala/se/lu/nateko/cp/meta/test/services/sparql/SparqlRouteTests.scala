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
import akka.pattern.after
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
import se.lu.nateko.cp.meta.test.services.sparql.TestDbFixture

import java.net.URI
import scala.concurrent.Await
import scala.concurrent.Future

import concurrent.duration.DurationInt

@DoNotDiscover
class SparqlRouteTests extends AsyncFunSpec with ScalatestRouteTest with TestDbFixture:

	import system.{log}

	val numberOfParallelQueries = 2
	private val reqOrigin = "https://example4567.icos-cp.eu"
	val sparqlConfig = new SparqlServerConfig(5, 2, 2, numberOfParallelQueries, 0, 10, 8388608, Seq("test@nateko.lu.se"))
	given default(using system: ActorSystem): RouteTestTimeout = RouteTestTimeout(10.seconds)

	val sparqlRoute: Future[Route] =
		db.repo.map: repo =>
			val rdf4jServer = Rdf4jSparqlServer(repo, sparqlConfig, log, system.scheduler)
			given ToResponseMarshaller[SparqlQuery] = rdf4jServer.marshaller
			given EnvriConfigs = Map(
				Envri.ICOS -> EnvriConfig(null, null, null, null, new URI("http://test.icos.eu/resources/"), null)
			)

			SparqlRoute.apply(sparqlConfig)

	def req(query: String, ip: String, additionalHeader: Option[HttpHeader] = None, origin: String = reqOrigin) =
		val otherHeaders = Seq(RawHeader("X-Forwarded-For", ip), Origin(HttpOrigin(origin))) ++ additionalHeader
		Post("/sparql", query).withHeaders(Accept(Rdf4jSparqlServer.csvSparql.mediaType), otherHeaders*)

	def testRoute(
		query: String, ip: String = "127.0.0.1", additionalHeader: Option[HttpHeader] = None, origin: String = reqOrigin
	)(test: => Assertion): Future[Assertion] =
		sparqlRoute map: route =>
			req(query, ip, additionalHeader, origin) ~> route ~> check(test)

	private def assertCORS(expectedOrigin: String = reqOrigin): Assertion =
		assert(header(`Access-Control-Allow-Origin`.name).get.value() === expectedOrigin)

	describe("SparqlRoute"):
		it("Correct query should produce correct result and get cached"):
			val uri = "https://meta.icos-cp.eu/objects/R5U1rVcbEQbdf9l801lvDUSZ"
			val query = s"""select * where { 
						VALUES ?s { <$uri> }
						?s ?p ?o } limit 1"""
			var firstResponse: String = ""

			testRoute(query):
				assert(status === StatusCodes.OK)
				assert(header("x-cache-status").get === RawHeader("X-Cache-Status", "MISS"))
				assertCORS()
				firstResponse = responseAs[String]
				assert(firstResponse.contains(uri))
			.flatMap: _ =>
				val anotherOrigin = "https://blabla.com"
				Thread.sleep(20) // to prevent hypothetically possible race condition in testing
				testRoute(query, origin = anotherOrigin):
					assert(status === StatusCodes.OK)
					assertCORS(anotherOrigin) // CORS headers are not cached
					assert(responseAs[String] === firstResponse)
					assert(header("x-cache-status").get == RawHeader("X-Cache-Status", "HIT"))

		it("Request with cache disabled should update the cache"):
			val uri = "https://meta.icos-cp.eu/objects/R5U1rVcbEQbdf9l801lvDUSZ"
			val query = s"""select * where { <$uri> ?p ?o } limit 30"""
			var firstResponse: String = ""

			testRoute(query, additionalHeader = Some(`Cache-Control`(`no-cache`))):
				firstResponse = responseAs[String]
				assert(header("x-cache-status").get === RawHeader("X-Cache-Status", "BYPASS"))
			.flatMap: _ =>
				testRoute(query):
					assert(responseAs[String] === firstResponse)
					assert(header("x-cache-status").get === RawHeader("X-Cache-Status", "HIT"))

		it("Syntax error in query"):
			val query = "selecct * where { ?s ?p ?o }"
			
			testRoute(query):
				assert(status === StatusCodes.BadRequest)
				assertCORS()
				assert(responseAs[String].contains("selecct"))

		it("'Biblioinfo' query on a broken object returns empty result list"):
			val objUri = "https://meta.icos-cp.eu/objects/a31A8q-hCILq74TM9GoIW9Yg"
			val query = s"""
				prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>

				select ?dobj ?cit where {
					VALUES ?dobj { <$objUri> }
					?dobj cpmeta:hasBiblioInfo ?cit .
				}
				"""

			testRoute(query):
				assert(status === StatusCodes.OK)
				assertCORS()
				assert(responseAs[String].trim == "dobj,cit")

		it("Broken object is excluded from a 'biblioinfo' query on multiple objects"):
			val objUri = "https://meta.icos-cp.eu/objects/a31A8q-hCILq74TM9GoIW9Yg"
			val query = s"""
				prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>

				select ?dobj ?cit where {
					{
						select ?dobj ?cit where {
							?dobj cpmeta:hasObjectSpec ?specUri .
							?dobj cpmeta:hasBiblioInfo ?cit .
						}
						limit 5
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
				assertCORS()
				assert(!responseAs[String].contains(objUri))

		val longRunningQuery = """
			prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
			select ?sumb2 where{
				?subm1 a cpmeta:DataSubmission .
				?sumb2 a cpmeta:DataSubmission .
				?dobj1 cpmeta:wasSubmittedBy ?subm1 .
				?dobj2 cpmeta:wasSubmittedBy ?subm2 .
				filter (?dobj1 = ?dobj2)
			}
			order by ?subm1
			offset 1000 limit 3
		"""

		it("Long running query should result in bad-request response"):
			testRoute(longRunningQuery):
				assertCORS()
				assert(status == StatusCodes.BadRequest)

		it("Exceeding SPARQL running quota results in Service Unavailable response to subsequent queries"):
			val uri = "https://meta.icos-cp.eu/objects/R5U1rVcbEQbdf9l801lvDUSZ"
			val ip = "127.0.1.1"

			val initRequests = Future.sequence(Seq(
				testRoute(longRunningQuery, ip):
					assert(status == StatusCodes.BadRequest), 
				testRoute(longRunningQuery, ip):
					assert(status == StatusCodes.BadRequest))
				)

			initRequests.flatMap: res =>
				testRoute(longRunningQuery, ip):
					assert(status == StatusCodes.ServiceUnavailable)

		it("Too many parallel queries result in bad-request responses"):
			val uri = "https://meta.icos-cp.eu/objects/R5U1rVcbEQbdf9l801lvDUSZ"
			val ip = "127.0.2.1"

			sparqlRoute.flatMap: route =>
				val request = req(longRunningQuery, ip, Some(`Cache-Control`(`no-cache`)))
				route(request)
				route(request)
				val query = s"""select * where { <$uri> ?p ?o } # query 3"""
				after(100.millis): // to ensure that the third query gets started last
					testRoute(query, ip):
						assertCORS()
						assert(status == StatusCodes.BadRequest)

end SparqlRouteTests
