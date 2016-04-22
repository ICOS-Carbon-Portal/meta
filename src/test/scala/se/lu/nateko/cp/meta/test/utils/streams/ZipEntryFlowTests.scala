package se.lu.nateko.cp.meta.test.utils.streams

import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.collection.immutable.Iterable
import akka.util.ByteString
import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.utils.streams.ZipEntryFlow
import akka.Done
import scala.concurrent.Future
import akka.stream.scaladsl.Sink
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import java.io.PipedOutputStream
import java.io.PipedInputStream
import java.util.zip.ZipOutputStream
import java.util.UUID

class ZipEntryFlowTests extends FunSuite with BeforeAndAfterAll{

	private implicit val system = ActorSystem("ZipEntryStreamingTests")
	private implicit val materializer = ActorMaterializer()
	import system.dispatcher

	override def afterAll() {
		system.terminate()
	}

	test("zipping single ByteString stream as a single file works correctly"){
		def row = (1 to 10).map(_ => UUID.randomUUID().toString).mkString("", " ", "\n")
		val elements = Iterable.range(0, 110).map(i => ByteString(row))
		val source = Source(elements)
		val zipped = ZipEntryFlow
			.getMultiEntryZipStream(Seq("testfile.txt" -> source))
			.reduce(_ ++ _)

		val zipBytes = Await.result(zipped.runWith(Sink.head), 2 second)
		println(s"Zipped to ${zipBytes.length} bytes")
	}

	ignore("piped streams and zipping test"){
		val os = new PipedOutputStream
		val is = new PipedInputStream(os)
		val zos = new ZipOutputStream(os)

//		zos.
	}

}