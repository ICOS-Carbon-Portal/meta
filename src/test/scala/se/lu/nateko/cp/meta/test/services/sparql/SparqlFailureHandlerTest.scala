package se.lu.nateko.cp.meta.test.services.sparql

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes
import akka.stream.Materializer
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.routes.SparqlRoute

import java.util.concurrent.CancellationException
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt

class SparqlFailureHandlerTest extends AsyncFunSpec with BeforeAndAfterAll{

	given system: ActorSystem = ActorSystem("YourSystem")
	given materializer: Materializer = Materializer(system)

	override protected def afterAll(): Unit =
		system.terminate()

	def getFailureSource(chunks: Int, exception: Exception): Source[ByteString, NotUsed] = Source
		.fromIterator(() => Iterator.from(1))
		.take(chunks)
		.flatMapConcat: i =>
			if i < chunks then Source.single(ByteString(s"chunk $i, ", "UTF-8"))
			else Source.failed(exception)

	def makeGoodSource(chunks: Int): Source[ByteString, NotUsed] = Source
		.fromIterator(() => Iterator.from(1))
		.take(chunks)
		.map:
			i => ByteString(s"chunk $i, ", "UTF-8")

	def handleErrors(in: Source[ByteString, NotUsed]): Future[HttpResponse] =
		SparqlRoute.handleSparqlFailures(HttpResponse(entity = HttpEntity(ContentTypes.`application/octet-stream`, in)))

	def assertStatusCode(in: Source[ByteString, NotUsed], status: StatusCode) = handleErrors(in).map: r =>
		assert(r.status === status)

	describe("SparqlFailureHandleTest"):

		it("Sending error in prologue results in InternalServerError"):
			val source = getFailureSource(2, new RuntimeException())
			assertStatusCode(source, StatusCodes.InternalServerError)

		it("Sending stream with an error after many elements results in a 200"):
			val source = getFailureSource(20, Exception())
			assertStatusCode(source, StatusCodes.OK)

		it("CancellationException results in a RequestTimeout response"):
			val source = getFailureSource(2, new CancellationException())
			assertStatusCode(source, StatusCodes.RequestTimeout)

		it("Sending stream without error results in a 200"):
			assertStatusCode(makeGoodSource(4), StatusCodes.OK)

		it("Sending stream with an error after many elements is handled by appending the error report at the end of the stream"):
			val msg = "### special message ###"
			val source = getFailureSource(20, Exception(msg))
			for
				r <- handleErrors(source)
				allBytes <- r.entity.dataBytes.toMat(Sink.reduce(_ ++ _))(Keep.right).run()
			yield
				assert(r.status === StatusCodes.OK)
				assert(allBytes.utf8String.contains(msg))

}
