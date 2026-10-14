package se.lu.nateko.cp.meta.test.services.external

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.*
import akka.pattern.after
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.time.{Millis, Seconds, Span}
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.{References, StaticObject}
import se.lu.nateko.cp.meta.services.citation.CitationStyle
import se.lu.nateko.cp.meta.services.external.{ExternalObjFetcher, ExternalProviders}
import spray.json.*

import java.net.URI
import java.time.Duration as JavaDuration
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

import ExternalTestObjects.*

class ExternalObjFetcherTests extends AnyFunSpec with BeforeAndAfterAll with Eventually:

	override given patienceConfig: PatienceConfig = PatienceConfig(Span(5, Seconds), Span(50, Millis))

	private given system: ActorSystem = ActorSystem("ExternalObjFetcherTests")

	private val responses = TrieMap.empty[String, () => Future[HttpResponse]]
	private val hits = TrieMap.empty[String, Int]

	private val route = path("objects" / Segment): name =>
		hits.updateWith(name)(n => Some(n.getOrElse(0) + 1))
		complete(responses.get(name).fold(Future.successful(HttpResponse(StatusCodes.NotFound)))(_()))

	private val binding = Await.result(Http().newServerAt("localhost", 0).bind(route), 10.seconds)
	private val port = binding.localAddress.getPort

	override def afterAll(): Unit =
		Await.ready(binding.unbind(), 10.seconds)
		Await.ready(system.terminate(), 10.seconds)

	private def url(name: String) = URI(s"http://localhost:$port/objects/$name")

	private def respond(name: String, resp: HttpResponse, delay: FiniteDuration): Unit =
		responses += name -> (() => after(delay)(Future.successful(resp)))

	private def serve(name: String, refs: References, pid: Option[String] = None, delay: FiniteDuration = 0.seconds): Unit =
		val json = (docObject(None, references = refs, pid = pid): StaticObject).toJson.compactPrint
		respond(name, HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, json)), delay)

	private def cited(text: String) = References.empty.copy(citationString = Some(text))

	private def serveFailure(name: String): Unit =
		respond(name, HttpResponse(StatusCodes.InternalServerError, entity = "remote failure"), 0.seconds)

	private def fetcher(
		ttl: JavaDuration = ExternalObjFetcher.defaultTtl,
		failureTtl: JavaDuration = ExternalObjFetcher.defaultFailureTtl,
		maxEntries: Int = ExternalObjFetcher.defaultMaxEntries
	) = ExternalObjFetcher(ExternalProviders(Map.empty), ttl, failureTtl, maxEntries)

	private def citation(f: ExternalObjFetcher, name: String, style: CitationStyle = CitationStyle.HTML) =
		f.getCitationEager(url(name), style)

	private def fetched(f: ExternalObjFetcher, name: String): Try[String] = eventually:
		val res = citation(f, name)
		assert(res.isDefined)
		res.get

	private def hitsOf(name: String): Int = hits.getOrElse(name, 0)

	describe("getCitationEager"):

		it("is empty while the object is being fetched, then returns its citation"):
			serve("pending", cited("Pending citation"), delay = 500.millis)
			val f = fetcher()
			assert(citation(f, "pending") === None)
			assert(fetched(f, "pending") === Success("Pending citation"))

		it("returns the citation of the requested style"):
			serve("styled", References.empty.copy(
				citationString = Some("HTML citation"),
				citationBibTex = Some("BibTex citation"),
				citationRis = Some("RIS citation")
			))
			val f = fetcher()
			assert(fetched(f, "styled") === Success("HTML citation"))
			assert(citation(f, "styled", CitationStyle.bibtex) === Some(Success("BibTex citation")))
			assert(citation(f, "styled", CitationStyle.ris) === Some(Success("RIS citation")))

		it("fails when the object has no citation of the requested style"):
			serve("htmlOnly", cited("HTML citation"))
			val f = fetcher()
			assert(fetched(f, "htmlOnly") === Success("HTML citation"))
			citation(f, "htmlOnly", CitationStyle.bibtex) match
				case Some(Failure(err)) => assert(err.getMessage === s"No bibtex citation in object at ${url("htmlOnly")}")
				case other => fail(s"Unexpected $other")

		it("fails when the remote server responds with an error"):
			serveFailure("broken")
			fetched(fetcher(), "broken") match
				case Failure(err) => assert(err.getMessage === s"Got 500 Internal Server Error from ${url("broken")}")
				case other => fail(s"Unexpected $other")

		it("fails when the response is not a static object"):
			respond("notAnObject", HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, "{}")), 0.seconds)
			assert(fetched(fetcher(), "notAnObject").isFailure)

	describe("getPidEager"):

		it("returns the PID of the fetched object"):
			serve("withPid", References.empty, pid = Some("11676/abc"))
			val f = fetcher()
			eventually(assert(f.getPidEager(url("withPid")) === Some("11676/abc")))

		it("is empty when the fetch failed"):
			serveFailure("pidBroken")
			val f = fetcher()
			assert(fetched(f, "pidBroken").isFailure)
			assert(f.getPidEager(url("pidBroken")) === None)

	describe("caching"):

		it("fetches an object only once while it is fresh"):
			serve("fresh", cited("Fresh citation"))
			val f = fetcher()
			assert(fetched(f, "fresh") === Success("Fresh citation"))
			(1 to 5).foreach(_ => assert(citation(f, "fresh") === Some(Success("Fresh citation"))))
			assert(hitsOf("fresh") === 1)

		it("serves a stale citation while re-fetching it once in the background"):
			serve("stale", cited("Old citation"))
			val f = fetcher(ttl = JavaDuration.ofMillis(300))
			assert(fetched(f, "stale") === Success("Old citation"))
			Thread.sleep(400)
			serve("stale", cited("New citation"), delay = 500.millis)
			(1 to 5).foreach(_ => assert(citation(f, "stale") === Some(Success("Old citation"))))
			eventually(assert(citation(f, "stale") === Some(Success("New citation"))))
			assert(hitsOf("stale") === 2)

		it("keeps reporting a failure while retrying the fetch"):
			serveFailure("flaky")
			val f = fetcher(failureTtl = JavaDuration.ofMillis(300))
			assert(fetched(f, "flaky").isFailure)
			Thread.sleep(400)
			serve("flaky", cited("Recovered citation"), delay = 500.millis)
			(1 to 5).foreach(_ => assert(citation(f, "flaky").exists(_.isFailure)))
			eventually(assert(citation(f, "flaky") === Some(Success("Recovered citation"))))

	describe("eviction"):

		it("evicts failed fetches first when the cache is full"):
			Seq("evictA", "evictB", "evictC", "evictE").foreach(name => serve(name, cited(name)))
			serveFailure("evictF")
			val f = fetcher(maxEntries = 4)
			Seq("evictA", "evictF", "evictB", "evictC", "evictE").foreach(fetched(f, _))
			Seq("evictA", "evictB", "evictC").foreach: name =>
				assert(citation(f, name) === Some(Success(name)))
				assert(hitsOf(name) === 1)
			fetched(f, "evictF")
			assert(hitsOf("evictF") === 2)

		it("evicts the oldest fetches when the cache is full"):
			Seq("oldA", "oldB", "oldC", "oldD", "oldE").foreach(name => serve(name, cited(name)))
			val f = fetcher(maxEntries = 4)
			Seq("oldA", "oldB", "oldC", "oldD", "oldE").foreach(fetched(f, _))
			Seq("oldB", "oldC", "oldD").foreach: name =>
				assert(citation(f, name) === Some(Success(name)))
				assert(hitsOf(name) === 1)
			assert(fetched(f, "oldA") === Success("oldA"))
			assert(hitsOf("oldA") === 2)
