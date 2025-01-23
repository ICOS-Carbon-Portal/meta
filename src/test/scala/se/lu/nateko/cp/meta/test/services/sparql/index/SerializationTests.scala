package se.lu.nateko.cp.meta.test.services.sparql.index

import akka.actor.Cancellable
import akka.actor.Scheduler
import akka.event.NoLogging
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.eclipse.rdf4j.common.transaction.TransactionSetting
import org.eclipse.rdf4j.model.util.Values
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.sail.NotifyingSail
import org.eclipse.rdf4j.sail.NotifyingSailConnection
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.helpers.NotifyingSailConnectionWrapper
import org.roaringbitmap.buffer.MutableRoaringBitmap
import org.scalatest.compatible.Assertion
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap.MinFilter
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObjectSpec
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.services.sparql.index.All
import se.lu.nateko.cp.meta.services.sparql.index.CategFilter
import se.lu.nateko.cp.meta.services.sparql.index.ContFilter
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch
import se.lu.nateko.cp.meta.services.sparql.index.Property
import se.lu.nateko.cp.meta.services.sparql.index.Station
import se.lu.nateko.cp.meta.services.sparql.index.SubmissionEnd
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
import se.lu.nateko.cp.meta.services.sparql.magic.IndexHandler
import se.lu.nateko.cp.meta.utils.rdf4j.Loading
import se.lu.nateko.cp.meta.utils.rdf4j.accessEagerly

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Using

class SerializationTests extends AsyncFunSpec{

	def printToBytesAndParseBack[T](obj: T): T =
		val os = ByteArrayOutputStream()
		val output = new Output(os)
		val kryo = IndexHandler.makeKryo
		kryo.writeClassAndObject(output, obj)
		output.close()
		val bytes = os.toByteArray()
		val hexdump = bytes
			.map: byte =>
				if byte > 31 && byte < 127 then " " + byte.toChar.toString
				else Sha256Sum.formatByte(byte)
			.sliding(10, 10)
			.map(_.mkString(" "))
			.mkString("\n")
		println(hexdump)
		val is = ByteArrayInputStream(bytes)
		val input = Input(is)
		kryo.readClassAndObject(input).asInstanceOf[T]


	def saveToBytes(idx: CpIndex)(using Kryo): Future[Array[Byte]] = {
		val os = ByteArrayOutputStream()
		IndexHandler.storeToStream(idx, os).map{_ => os.toByteArray}
	}

	def loadFromBytes(arr: Array[Byte])(using Kryo): Future[IndexData] = {
		val is = ByteArrayInputStream(arr)
		IndexHandler.restoreFromStream(is)
	}

	def roundTrip(sail: Sail)(using Kryo) = //: Future[(CpIndex, CpIndex)] =
		for(
			idx <- Future(CpIndex(sail, Future.never, 5)(using NoLogging));
			arr <- saveToBytes(idx);
			data <- loadFromBytes(arr)
		) yield idx -> CpIndex(sail, Future.never, data)(using NoLogging)

	def smallRepo = Loading.fromResource("/rdf/someDobjsAndSpecs.ttl", "http://test.icos-cp.eu/blabla", RDFFormat.TURTLE)


	describe("Small index created, object deleted, leaving orphan data type"):
		it("successfully performs serialization/deserialization round trip"):
			given Kryo = IndexHandler.makeKryo
			val repo = smallRepo
			val idx = CpIndex(repo.getSail, Future.never, 5)(using NoLogging)
			val cpmeta = CpmetaVocab(repo.getValueFactory)
			val idxHandler = IndexHandler(DummyScheduler)
			val listener = idxHandler.getListener(repo.getSail, cpmeta, idx, Future.never)

			Using(repo.getSail.getConnection): baseconn =>
				val conn = baseconn.asInstanceOf[NotifyingSailConnection]
				conn.addConnectionListener(listener)
				//deleting an object, leaving one data type "an orphan"
				val delObj = repo.getValueFactory.createIRI("https://meta.icos-cp.eu/objects/hoidzqcaqmCU3mOZ435r2crG")
				val toRemove = conn.getStatements(delObj, null, null, false).stream().toArray()
				conn.begin()
				conn.removeStatements(delObj, null, null)
				conn.commit()
			repo.shutDown()

			for arr <- saveToBytes(idx); _ <- loadFromBytes(arr)
				yield succeed


	describe("CpIndex created from test RDF in a turtle file, and round-tripped"){
		val repo = smallRepo
		given Kryo = IndexHandler.makeKryo
		val idxFut = roundTrip(repo.getSail).andThen{_ =>
			repo.shutDown()
		}
		val toData: CpIndex => IndexData = _.serializableData.asInstanceOf[IndexData]
		val droughtObjHash = Sha256Sum.fromBase64Url("8KgcKLxNMRE-AtEEEFH6e_yd").get
		val etcObjHash = Sha256Sum.fromBase64Url("hoidzqcaqmCU3mOZ435r2crG").get

		def origAndCopy[T](title: String, expectation: T)(theTest: CpIndex => T) = it(title){
			idxFut.map{(idx0, idx1) =>
				assertResult(expectation, "(original index)")(theTest(idx0))
				assertResult(expectation, "(de-/serialized index)")(theTest(idx1))
			}
		}

		// it("serializes IndexData fragment"):
		// 	idxFut.map: fresh =>
		// 		val data = toData(fresh)
		// 		val roundTrip = printToBytesAndParseBack(data)//, classOf[MutableRoaringBitmap])
		// 		println("Original:")
		// 		println(data)// foreach println
		// 		println("Round trip result:")
		// 		println(roundTrip)
		// 		assert(true)

		origAndCopy("should contain expected number of objects", 2)(_.size)

		origAndCopy("should correctly filter by submission date", 1){idx =>
			val filter = ContFilter(SubmissionEnd, MinFilter(Instant.parse("2019-06-01T00:00:00Z").toEpochMilli, false))
			val fetch = DataObjectFetch(filter, None, 0)
			val objs = idx.fetch(fetch).toSeq
			objs.size
		}

		origAndCopy("both objects must be marked as initialized", Array(0,1)){
			toData.andThen(_.initOk.toArray)
		}

		origAndCopy("contain expected hashsums", Set(etcObjHash, droughtObjHash)){
			toData.andThen(_.objs.map(_.hash).toSet)
		}

		origAndCopy("correctly filters by station", 1){idx =>
			val fiSii = Values.iri("http://meta.icos-cp.eu/resources/stations/ES_FI-Sii")
			val filter = CategFilter(Station, Seq(Some(fiSii)))
			idx.fetch(DataObjectFetch(filter, None, 0)).toSeq.size
		}

		origAndCopy("correctly fetches dobjs", 2){ idx =>
			val res = idx.fetch(DataObjectFetch(And(List(Exists(FileName), Exists(SubmissionEnd))), Some(SortBy(SubmissionEnd, true)),0)).toSeq
			res.size
		}

		origAndCopy("has two stations", 2)(
			toData.andThen(_.categMaps(Station).size)
		)
	}
}

object DummyScheduler extends Scheduler:
	def maxFrequency: Double = 1000d
	def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable)(using ExecutionContext): Cancellable =
		runnable.run()
		Cancellable.alreadyCancelled

	def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(using ExecutionContext): Cancellable =
		runnable.run()
		Cancellable.alreadyCancelled
