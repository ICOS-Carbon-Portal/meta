package se.lu.nateko.cp.meta.test.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Host, `Access-Control-Allow-Origin`}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.StatisticsClient
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.routes.StatisticsRoute
import se.lu.nateko.cp.meta.test.TestConfig
import se.lu.nateko.cp.meta.test.TestConfig.given
import se.lu.nateko.cp.meta.{RestheartConfig, StatsClientConfig}
import eu.icoscp.envri.Envri
import spray.json.*

/**
 * The statistics services are not available in tests, so all the counts come out as
 * "unavailable". What is under test here is the routing and the response shape.
 */
class StatisticsRouteTests extends AnyFunSpec with ScalatestRouteTest:

	private val statsConf = StatsClientConfig(
		downloadsUri = "http://127.0.0.1:1/stats/api/downloadCount",
		previews = RestheartConfig("http://127.0.0.1:1", Map(Envri.ICOS -> "db"))
	)
	private val route = StatisticsRoute(new StatisticsClient(statsConf, TestConfig.envriConfs))

	private val hash = Sha256Sum.fromHex("b" * 64).get
	private val metaHost = Host("meta.icos-cp.eu")

	describe("StatisticsRoute"):

		it("serves object statistics on the object's landing page path"):
			Get(s"/objects/${hash.base64Url}/statistics").withHeaders(metaHost) ~> route ~> check:
				assert(status === StatusCodes.OK)
				assert(responseAs[String].parseJson.isInstanceOf[JsObject])
				assert(headers.contains(`Access-Control-Allow-Origin`.*))

		it("accepts a hex-encoded hashsum as well"):
			Get(s"/objects/${hash.hex}/statistics").withHeaders(metaHost) ~> route ~> check:
				assert(status === StatusCodes.OK)

		it("serves collection statistics on the collection's landing page path"):
			Get(s"/collections/${hash.id}/statistics").withHeaders(metaHost) ~> route ~> check:
				assert(status === StatusCodes.OK)
				assert(responseAs[String].parseJson.isInstanceOf[JsObject])

		it("rejects paths whose last segment is not a hashsum"):
			Get("/objects/not-a-hashsum/statistics").withHeaders(metaHost) ~> route ~> check:
				assert(!handled)

		it("rejects unknown ENVRI hosts"):
			Get(s"/objects/${hash.base64Url}/statistics")
				.withHeaders(Host("unknown.example.org")) ~> route ~> check:
					assert(status === StatusCodes.BadRequest)
