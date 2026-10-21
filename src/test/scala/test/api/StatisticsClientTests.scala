package se.lu.nateko.cp.meta.test.api

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.*
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.pattern.after
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.testkit.TestProbe
import akka.util.ByteString
import eu.icoscp.envri.Envri
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.{RestheartConfig, StatsClientConfig}
import se.lu.nateko.cp.meta.api.StatisticsClient
import se.lu.nateko.cp.meta.utils.akkahttp.HttpClient

import java.net.{ConnectException, URI}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*

class StatisticsClientTests extends AnyFunSuite with BeforeAndAfterAll {
	private given system: ActorSystem = ActorSystem("StatisticsClientTests")
	private given Materializer = Materializer(system)
	private given Envri = Envri.ICOS
	private given scala.concurrent.ExecutionContext = system.dispatcher

	private val base = "http://statistics.test"

	override def afterAll(): Unit = {
		Await.result(system.terminate(), 10.seconds)
		super.afterAll()
	}

	test("healthy statistics are still returned") {
		withResponse(_ => Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, """[{"downloadCount":2},{"downloadCount":3}]""")))) { client =>
			assert(Await.result(count(client), 3.seconds).contains(5))
		}
	}

	test("response times do not build up upon repeated connection errors") {
		var requests = 0
		var failedConnects = 0
		val client = clientWith { (request, settings) =>
			requests += 1
			assert(request.method == HttpMethods.GET)
			assert(request.uri == Uri(s"$base?hashId=test"))
			// emulate the connection pool's exponential backoff between consecutive failed connects
			def attempt(retriesLeft: Int): Future[HttpResponse] = {
				val backoffMs = math.min(
					settings.baseConnectionBackoff.toMillis * math.pow(2, failedConnects),
					settings.maxConnectionBackoff.toMillis.toDouble
				).toLong
				failedConnects += 1
				after(backoffMs.millis)(Future.failed(ConnectException("Connection refused")))
					.recoverWith { case _ if retriesLeft > 0 => attempt(retriesLeft - 1) }
			}
			attempt(settings.maxRetries)
		}
		val responseTimes = (1 to 8).map { _ =>
			val start = System.nanoTime()
			assert(Await.result(count(client), 3.seconds).isEmpty)
			(System.nanoTime() - start).nanos
		}
		assert(requests == 8)
		// with unbounded backoff, later requests would only be answered by the stats timeout
		assert(responseTimes.forall(_ < 900.millis), s"response times: ${responseTimes.map(_.toMillis)} ms")
		// once the backoff is saturated, response times must stop growing
		assert(responseTimes.last < responseTimes(6) + 200.millis, s"response times: ${responseTimes.map(_.toMillis)} ms")
	}

	test("an unavailable statistics server logs the failure and request URL") {
		val client = unavailableClient()
		val logs = TestProbe()
		system.eventStream.subscribe(logs.ref, classOf[Logging.Warning])
		try {
			assert(Await.result(count(client), 2.seconds).isEmpty)
			logs.expectMsgPF(3.seconds) {
				case warning: Logging.Warning =>
					assert(warning.logClass == classOf[StatisticsClient])
					val message = warning.message.toString
					assert(message.startsWith("Problem fetching statistics ("))
					assert(message.contains("Connection refused"))
					assert(message.endsWith(s"\nfrom: $base?hashId=test"))
			}
		} finally {
			system.eventStream.unsubscribe(logs.ref)
		}
	}

	test("slow HTTP responses fall back to no statistics and later requests succeed") {
		val fast = new java.util.concurrent.atomic.AtomicBoolean(false)
		withResponse(_ => after(if (fast.get()) Duration.Zero else 1500.millis)(
			Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, """[{"downloadCount":5}]""")))
		)) { client =>
			for (_ <- 1 to 6) {
				assert(Await.result(count(client), 1400.millis).isEmpty)
			}
			fast.set(true)
			assert(Await.result(count(client), 3.seconds).contains(5))
		}
	}

	test("the deadline also covers a slow response body") {
		withResponse(_ => Future.successful(HttpResponse(entity = HttpEntity.Chunked.fromData(
			ContentTypes.`application/json`,
			Source.single(ByteString("""[{"downloadCount":5}]""")).delay(1500.millis)
		)))) { client =>
			assert(Await.result(count(client), 1400.millis).isEmpty)
		}
	}

	private def clientWith(response: (HttpRequest, ConnectionPoolSettings) => Future[HttpResponse]): StatisticsClient = {
		val http = new HttpClient {
			def singleRequest(request: HttpRequest, settings: ConnectionPoolSettings): Future[HttpResponse] =
				response(request, settings)
		}
		new StatisticsClient(StatsClientConfig(base, RestheartConfig(base, Map(Envri.ICOS -> "test"))), Map.empty, http)
	}

	private def withResponse(response: HttpRequest => Future[HttpResponse])(test: StatisticsClient => Unit): Unit =
		test(clientWith((request, _) => response(request)))

	private def unavailableClient(): StatisticsClient =
		clientWith((_, _) => Future.failed(ConnectException("Connection refused")))

	private def count(client: StatisticsClient): Future[Option[Int]] =
		client.getCollDownloadCount(URI("https://example.org/collections/test"))
}
