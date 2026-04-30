package se.lu.nateko.cp.meta.test.utils.streams

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.utils.streams.ZipEntryFlow

import scala.collection.immutable.Iterable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class ZipEntryFlowTests extends AnyFunSuite with BeforeAndAfterAll{

	private given system: ActorSystem = ActorSystem("ZipEntryStreamingTests")

	override def afterAll(): Unit = {
		system.terminate()
	}

	test("zipping single ByteString stream as a single file works correctly"){
		def row(i: Int) = (1 to 10).map(j => s"word #$j").mkString(s"Line #$i : ", ", ", "\n")
		val elements = Iterable.range(0, 100).map(i => ByteString(row(i)))
		val source = Source(elements)
		val zipped = ZipEntryFlow
			.getMultiEntryZipStream(Seq("testfile.txt" -> source))
			.reduce(_ ++ _)

		val zipBytes = Await.result(zipped.runWith(Sink.head), 2 second)
		assert(zipBytes.length === 448)
	}

}
