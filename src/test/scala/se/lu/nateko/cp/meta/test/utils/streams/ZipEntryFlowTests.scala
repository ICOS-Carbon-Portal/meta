package se.lu.nateko.cp.meta.test.utils.streams

import scala.language.postfixOps

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import akka.actor.ActorSystem
import akka.stream.Materializer

import scala.collection.immutable.Iterable
import akka.util.ByteString
import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.utils.streams.ZipEntryFlow
import akka.stream.scaladsl.Sink
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

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
