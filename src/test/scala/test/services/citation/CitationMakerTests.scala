package se.lu.nateko.cp.meta.test.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.*
import akka.pattern.after
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.time.{Millis, Seconds, Span}
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.{EnvriConfigs, Person, References, StaticObject, UriResource}
import se.lu.nateko.cp.meta.services.citation.*
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab, ExternalProviders}
import spray.json.*

import java.net.URI
import java.time.Duration as JavaDuration
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

import se.lu.nateko.cp.meta.test.services.ExternalTestObjects.*

class CitationMakerTests extends AnyFunSpec with BeforeAndAfterAll with Eventually:

	override given patienceConfig: PatienceConfig = PatienceConfig(Span(5, Seconds), Span(50, Millis))

	private given system: ActorSystem = ActorSystem("CitationMakerTests")
	private given EnvriConfigs = envriConfigs
	private given Envri = Envri.SITES

	private val remoteCitation = "Remote ICOS citation"

	private def objJson(refs: References): String =
		(docObject(None, references = refs): StaticObject).toJson.compactPrint

	private def jsonEntity(refs: References) = HttpEntity(ContentTypes.`application/json`, objJson(refs))

	private val route =
		path("objects" / "cited")(complete(jsonEntity(References.empty.copy(citationString = Some(remoteCitation))))) ~
		path("objects" / "uncited")(complete(jsonEntity(References.empty))) ~
		path("objects" / "broken")(complete(StatusCodes.InternalServerError -> "remote failure")) ~
		path("objects" / "slowBroken")(complete(
			after(500.millis)(Future.successful(HttpResponse(StatusCodes.InternalServerError, entity = "remote failure")))
		))

	private val binding = Await.result(Http().newServerAt("localhost", 0).bind(route), 10.seconds)
	private val port = binding.localAddress.getPort

	private def remoteUrl(name: String) = URI(s"http://localhost:$port/objects/$name")

	private val doiCiter = new PlainDoiCiter:
		def getCitationEager(doi: Doi, style: CitationStyle): Option[Try[String]] = doi.suffix.toLowerCase match
			case "cited"   => Some(Success("DOI citation"))
			case "broken"  => Some(Failure(Exception("DOI service down")))
			case _         => None
		def getDoiEager(doi: Doi): Option[Try[DoiMeta]] = None

	private val providers = ExternalProviders(Map(Envri.SITES -> Seq(provider("localhost"))))

	private val fetcher = ExternalObjFetcher(providers)

	private def citerWith(fetcher: ExternalObjFetcher) =
		val factory = SimpleValueFactory.getInstance
		CitationMaker(doiCiter, CpVocab(factory), CpmetaVocab(factory), MetaCoreConfig.default, fetcher)

	private val citer = citerWith(fetcher)

	override def afterAll(): Unit =
		Await.ready(binding.unbind(), 10.seconds)
		Await.ready(system.terminate(), 10.seconds)

	describe("remoteCitation"):

		it("is not applicable to local objects without DOI"):
			assert(citer.remoteCitation(docObject(Some(localAccessUrl(using sitesConf)))) === RemoteCitation.NotApplicable)

		it("is not applicable to external objects from unknown hosts"):
			val obj = docObject(Some(URI("https://unknown.example.org/objects/x")))
			assert(citer.remoteCitation(obj) === RemoteCitation.NotApplicable)

		it("is expected without error while the DOI citation is pending"):
			assert(citer.remoteCitation(docObject(None, doi = Some("10.1234/pending"))) === RemoteCitation.Expected(None))

		it("is expected without error once the DOI citation is available"):
			assert(citer.remoteCitation(docObject(None, doi = Some("10.1234/cited"))) === RemoteCitation.Expected(None))

		it("reports the DOI citation error"):
			assert(
				citer.remoteCitation(docObject(None, doi = Some("10.1234/broken"))) ===
				RemoteCitation.Expected(Some("Error fetching DOI citation: DOI service down"))
			)

		it("prefers the DOI citation over the external one"):
			val obj = docObject(Some(remoteUrl("cited")), doi = Some("10.1234/broken"))
			assert(citer.remoteCitation(obj) === RemoteCitation.Expected(Some("Error fetching DOI citation: DOI service down")))

		it("is expected for external objects without DOI, and the citation arrives"):
			val obj = docObject(Some(remoteUrl("cited")))
			assert(citer.remoteCitation(obj) === RemoteCitation.Expected(None))
			eventually:
				assert(fetcher.getCitationEager(remoteUrl("cited"), CitationStyle.HTML) === Some(Success(remoteCitation)))
			assert(citer.remoteCitation(obj) === RemoteCitation.Expected(None))

		it("reports an error when the external object cannot be fetched"):
			val obj = docObject(Some(remoteUrl("broken")))
			eventually:
				citer.remoteCitation(obj) match
					case RemoteCitation.Expected(Some(err)) =>
						assert(err.startsWith("Error fetching external citation: Got 500"))
					case other => fail(s"Unexpected $other")

		it("keeps reporting the error while retrying the fetch"):
			val retryingCiter = citerWith(ExternalObjFetcher(providers, failureTtl = JavaDuration.ofMillis(300)))
			val obj = docObject(Some(remoteUrl("slowBroken")))
			val error = eventually:
				retryingCiter.remoteCitation(obj) match
					case err @ RemoteCitation.Expected(Some(_)) => err
					case other => fail(s"Unexpected $other")
			Thread.sleep(400)
			(1 to 5).foreach: _ =>
				assert(retryingCiter.remoteCitation(obj) === error)

		it("reports an error when the external object has no citation"):
			val obj = docObject(Some(remoteUrl("uncited")))
			eventually:
				citer.remoteCitation(obj) match
					case RemoteCitation.Expected(Some(err)) => assert(err.contains("No HTML citation"))
					case other => fail(s"Unexpected $other")

	describe("formatPersonName"):

		def format(firstName: String, lastName: String = "Schmid"): String =
			val person = Person(UriResource(new URI("https://example.org/person"), None, Nil), firstName, lastName, None, None)
			CitationMaker.formatPersonName(person)

		it("uses all given-name initials"):
			assert(format("Hans Peter") == "Schmid, H.P.")

		it("single given names retain their existing format"):
			assert(format("Ingo", "Völksch") == "Völksch, I.")

		it("ignores leading, trailing and repeated whitespace including nonbreaking spaces"):
			assert(format("  Hans\t Peter\n ") == "Schmid, H.P.")
			assert(format("Hans Peter") == "Schmid, H.P.")

		it("preserves hyphens between initials"):
			assert(format("Jean-Pierre", "Dupont") == "Dupont, J.-P.")
			assert(format("Hans Peter-Paul") == "Schmid, H.P.-P.")

		it("empty given names remain invalid so broken objects do not produce biblioinfo"):
			intercept[IllegalArgumentException]:
				format("")

		it("whitespace-only given names are invalid, including Unicode whitespace"):
			Seq(" ", " \t\n ", " ", " ").foreach: name =>
				intercept[IllegalArgumentException]:
					format(name)

		it("ignores empty hyphenated components and preserves accented initials"):
			assert(format("-Hans--Peter-") == "Schmid, H.-P.")
			assert(format("---") == "Schmid")
			assert(format("Émile André") == "Schmid, É.A.")
