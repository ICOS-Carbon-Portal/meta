package se.lu.nateko.cp.meta.test.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.testkit.ScalatestRouteTest
import eu.icoscp.envri.Envri
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.cpauth.core.PublicAuthConfig
import se.lu.nateko.cp.meta.onto.{InstOnto, Onto}
import se.lu.nateko.cp.meta.routes.{AuthenticationRouting, MetadataEntryRouting}
import se.lu.nateko.cp.meta.test.TestConfig
import se.lu.nateko.cp.meta.test.TestConfig.given
import spray.json.*
import spray.json.DefaultJsonProtocol.*

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MetadataEntryRoutingTests extends AnyFunSpec with ScalatestRouteTest:

	private val onto = new Onto(TestConfig.owlOnto)
	private val instOnto = new InstOnto(TestConfig.instServer, onto)
	private val authRouting = new AuthenticationRouting(Map.empty[Envri, PublicAuthConfig])
	private val route = new MetadataEntryRouting(authRouting).singleOntoRoute(instOnto, Seq.empty)

	private val classUri = new URI(TestConfig.ontUri + "Station")
	private val encodedClassUri = URLEncoder.encode(classUri.toString, StandardCharsets.UTF_8)

	describe("MetadataEntryRouting getIndividualsSparql"):

		it("returns a query payload for a recognized ENVRI host"):
			Get(s"/getIndividualsSparql?classUri=$encodedClassUri")
				.withHeaders(Host("meta.icos-cp.eu")) ~> route ~> check:
					assert(status === StatusCodes.OK)
					val responseJson = responseAs[String].parseJson.asJsObject
					val query = responseJson.fields("query").convertTo[String]
					assert(query.contains("SELECT ?s"))
					assert(query.contains("""FILTER(STRSTARTS(STR(?s), "http://meta.icos-cp.eu/"))"""))

		it("returns bad request for unknown host"):
			Get(s"/getIndividualsSparql?classUri=$encodedClassUri")
				.withHeaders(Host("unknown.example.org")) ~> route ~> check:
					assert(status === StatusCodes.BadRequest)
					assert(responseAs[String].contains("Unexpected host"))
