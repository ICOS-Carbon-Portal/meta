package se.lu.nateko.cp.meta.test.services.sparql

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.RouteResult.Complete
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.routes.SparqlCache

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class SparqlCacheTests extends AnyFunSuite with BeforeAndAfterAll:

	private given system: ActorSystem = ActorSystem("SparqlCacheTests")

	override protected def afterAll(): Unit =
		try Await.result(system.terminate(), 5.seconds)
		finally super.afterAll()

	private def await[T](fut: Future[T]): T = Await.result(fut, 3.seconds)

	private def key(n: Int) = Sha256Sum(Array.fill[Byte](32)(n.toByte))

	private def streamed(source: Source[ByteString, Any]) =
		HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, source))

	private def fetch(cache: SparqlCache, key: Sha256Sum, resp: => HttpResponse): HttpResponse =
		await(cache(key, () => Future.successful(Complete(resp)))) match
			case Complete(r) => r
			case other => fail(s"Expected a complete response, got $other")

	private def body(resp: HttpResponse): ByteString =
		await(resp.entity.dataBytes.runWith(Sink.fold(ByteString.empty)(_ ++ _)))

	test("Cancelling a response must not cache a partial result for subsequent requests"):
		val cache = SparqlCache(1024)
		val prefix = ByteString("result\nfirst row\n")
		val fullResult = prefix ++ ByteString("second row\n")
		val (producer, source) = TestSource.probe[ByteString].preMaterialize()

		val client = fetch(cache, key(0), streamed(source)).entity.dataBytes.runWith(TestSink.probe[ByteString])
		client.request(1)
		producer.sendNext(prefix)
		client.expectNext(prefix)
		client.cancel()
		producer.expectCancellation()
		// Cache updates may run asynchronously after stream termination. Allow them to settle
		// before issuing the next request, so it cannot race ahead of a poisoned entry.
		producer.expectNoMessage(200.millis)

		val next = fetch(cache, key(0), HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, fullResult)))
		assert(body(next).utf8String === fullResult.utf8String)

	test("A normally completed streamed response is cached"):
		val cache = SparqlCache(1024)
		val fullResult = ByteString("first row\nsecond row\n")
		val first = fetch(cache, key(1), streamed(Source(fullResult.grouped(10).toList)))
		assert(body(first) == fullResult)
		assert(cache.contains(key(1)))
		assert(body(fetch(cache, key(1), fail("Expected a cache hit"))) == fullResult)

	test("A streamed response exceeding the cache limit is delivered without being cached"):
		val cache = SparqlCache(4)
		val first = fetch(cache, key(2), streamed(Source(List(ByteString("1234"), ByteString("5")))))
		assert(body(first) == ByteString("12345"))
		assert(!cache.contains(key(2)))

	test("A failed response stream is not cached"):
		val cache = SparqlCache(1024)
		val error = new IllegalStateException("Query failed")
		val first = fetch(cache, key(3), streamed(Source.single(ByteString("first row\n")).concat(Source.failed(error))))
		val thrown = intercept[IllegalStateException](body(first))
		assert(thrown eq error)
		assert(!cache.contains(key(3)))

end SparqlCacheTests
