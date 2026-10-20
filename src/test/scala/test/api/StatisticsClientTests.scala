package se.lu.nateko.cp.meta.test.api

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.*
import akka.pattern.after
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import eu.icoscp.envri.Envri
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.{RestheartConfig, StatsClientConfig}
import se.lu.nateko.cp.meta.api.StatisticsClient

import java.net.URI
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*

class StatisticsClientTests extends AnyFunSuite with BeforeAndAfterAll:
	private given system: ActorSystem = ActorSystem("StatisticsClientTests")
	private given Materializer = Materializer(system)
	private given Envri = Envri.ICOS
	private given scala.concurrent.ExecutionContext = system.dispatcher

	private def withServer(response: HttpRequest => Future[HttpResponse])(test: StatisticsClient => Unit): Unit =
		val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(response), 10.seconds)
		val base = s"http://127.0.0.1:${binding.localAddress.getPort}"
		val client = StatisticsClient(StatsClientConfig(base, RestheartConfig(base, Map(Envri.ICOS -> "test"))), Map.empty)
		try test(client)
		finally Await.result(binding.terminate(3.seconds), 5.seconds)

	private def count(client: StatisticsClient): Future[Option[Int]] =
		client.getCollDownloadCount(URI("https://example.org/collections/test"))

	test("healthy statistics are still returned"):
		withServer(_ => Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, """[{"downloadCount":2},{"downloadCount":3}]""")))): client =>
			assert(Await.result(count(client), 3.seconds).contains(5))

	test("repeated refused connections do not accumulate backoff delays"):
		val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(_ => Future.successful(HttpResponse())), 10.seconds)
		val base = s"http://127.0.0.1:${binding.localAddress.getPort}"
		Await.result(binding.unbind(), 5.seconds)
		val client = StatisticsClient(StatsClientConfig(base, RestheartConfig(base, Map(Envri.ICOS -> "test"))), Map.empty)
		for _ <- 1 to 12 do
			assert(Await.result(count(client), 2.seconds).isEmpty)

	test("slow headers fall back to no statistics and late responses leave the pool usable"):
		val fast = new java.util.concurrent.atomic.AtomicBoolean(false)
		withServer(_ => after(if fast.get() then Duration.Zero else 1500.millis)(
			Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, """[{"downloadCount":5}]""")))
		)): client =>
			for _ <- 1 to 6 do
				assert(Await.result(count(client), 1400.millis).isEmpty)
			fast.set(true)
			assert(Await.result(count(client), 3.seconds).contains(5))

	test("the deadline also covers a slow response body"):
		withServer(_ => Future.successful(HttpResponse(entity = HttpEntity.Chunked.fromData(
			ContentTypes.`application/json`,
			Source.single(ByteString("""[{"downloadCount":5}]""")).delay(1500.millis)
		)))): client =>
			assert(Await.result(count(client), 1400.millis).isEmpty)

	override def afterAll(): Unit =
		Await.result(Http().shutdownAllConnectionPools(), 10.seconds)
		Await.result(system.terminate(), 10.seconds)
		super.afterAll()
