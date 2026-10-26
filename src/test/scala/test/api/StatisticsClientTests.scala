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
	private val requestUrl = s"$base?hashId=test"

	override def afterAll(): Unit = {
		Await.result(system.terminate(), 10.seconds)
		super.afterAll()
	}

	test("repeated connection errors are logged and response times do not build up") {
		val pool = UnreachablePool()
		var requests = 0
		// the stats timeout must exceed the max connection backoff, for connection errors to be reported as such
		val client = clientWith(statsTimeout = 2.seconds) { (request, settings) =>
			requests += 1
			assert(request.method == HttpMethods.GET)
			assert(request.uri == Uri(requestUrl))
			pool.request(settings)
		}
		withWarningLogs { logs =>
			val responseTimes = (1 to 8).map(_ => timed(assert(awaitCount(client, 3.seconds).isEmpty)))
			val timesClue = s"response times: ${responseTimes.map(_.toMillis)} ms"

			assert(requests == 8)
			// with unbounded backoff, later requests would only be answered by the stats timeout
			assert(responseTimes.forall(_ < 1500.millis), timesClue)
			// once the backoff is saturated, response times must stop growing
			assert(responseTimes.last < responseTimes(6) + 200.millis, timesClue)
			// every failure is logged with its cause and request URL
			logs.receiveN(8, 3.seconds).foreach(assertConnectionRefusedWarning)
		}
	}

	test("slow HTTP responses or bodies fall back to no statistics and later healthy requests succeed") {
		// slower than the stats timeout, but well within the await, so only the timeout can produce the fallback
		val tooSlow = 1.minute
		var headersDelay: FiniteDuration = tooSlow
		var bodyDelay: FiniteDuration = Duration.Zero
		val client = clientWith(statsTimeout = 50.millis) { (_, _) =>
			jsonResponse("""[{"downloadCount":2},{"downloadCount":3}]""", headersDelay, bodyDelay)
		}

		// slow headers
		assert(awaitCount(client, 3.seconds).isEmpty)

		// slow body
		headersDelay = Duration.Zero
		bodyDelay = tooSlow
		assert(awaitCount(client, 3.seconds).isEmpty)

		// healthy again
		bodyDelay = Duration.Zero
		assert(awaitCount(client, 3.seconds).contains(5))
	}

	/** Emulates a connection pool to an unreachable server: every connect is refused,
	  * after a backoff that doubles with each consecutive failed connect, up to the configured maximum. */
	private class UnreachablePool {
		private var failedConnects = 0

		def request(settings: ConnectionPoolSettings): Future[HttpResponse] = {
			def attempt(retriesLeft: Int): Future[HttpResponse] =
				after(nextBackoff(settings))(Future.failed(ConnectException("Connection refused")))
					.recoverWith { case _ if retriesLeft > 0 => attempt(retriesLeft - 1) }
			attempt(settings.maxRetries)
		}

		private def nextBackoff(settings: ConnectionPoolSettings): FiniteDuration = {
			val backoffMs = math.min(
				settings.baseConnectionBackoff.toMillis * math.pow(2, failedConnects),
				settings.maxConnectionBackoff.toMillis.toDouble
			)
			failedConnects += 1
			backoffMs.toLong.millis
		}
	}

	private def jsonResponse(json: String, headersDelay: FiniteDuration, bodyDelay: FiniteDuration): Future[HttpResponse] = {
		val body = Source.future(after(bodyDelay)(Future.successful(ByteString(json))))
		after(headersDelay)(Future.successful(HttpResponse(entity = HttpEntity.Chunked.fromData(ContentTypes.`application/json`, body))))
	}

	private def assertConnectionRefusedWarning(msg: AnyRef): Unit = msg match {
		case warning: Logging.Warning =>
			assert(warning.logClass == classOf[StatisticsClient])
			val message = warning.message.toString
			assert(message.startsWith("Problem fetching statistics ("))
			assert(message.contains("Connection refused"))
			assert(message.endsWith(s"\nfrom: $requestUrl"))
		case other => fail(s"unexpected message: $other")
	}

	private def withWarningLogs(test: TestProbe => Unit): Unit = {
		val logs = TestProbe()
		system.eventStream.subscribe(logs.ref, classOf[Logging.Warning])
		try test(logs) finally system.eventStream.unsubscribe(logs.ref)
	}

	private def timed(action: => Unit): FiniteDuration = {
		val start = System.nanoTime()
		action
		(System.nanoTime() - start).nanos
	}

	private def clientWith(statsTimeout: FiniteDuration = 1.second)(
		response: (HttpRequest, ConnectionPoolSettings) => Future[HttpResponse]
	): StatisticsClient = {
		val http = new HttpClient {
			def singleRequest(request: HttpRequest, settings: ConnectionPoolSettings): Future[HttpResponse] =
				response(request, settings)
		}
		new StatisticsClient(StatsClientConfig(base, RestheartConfig(base, Map(Envri.ICOS -> "test"))), Map.empty, http, statsTimeout)
	}

	private def awaitCount(client: StatisticsClient, timeout: FiniteDuration): Option[Int] =
		Await.result(client.getCollDownloadCount(URI("https://example.org/collections/test")), timeout)
}
