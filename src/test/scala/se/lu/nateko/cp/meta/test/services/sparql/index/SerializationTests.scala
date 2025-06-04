package se.lu.nateko.cp.meta.test.services.sparql.index

import akka.actor.{Cancellable, Scheduler}
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import org.eclipse.rdf4j.model.util.Values
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.sail.{NotifyingSailConnection, Sail}
import org.scalatest.compatible.Assertion
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap.MinFilter
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.index.{
	And,
	CategFilter,
	ContFilter,
	DataObjectFetch,
	Exists,
	FileName,
	SortBy,
	Station,
	SubmissionEnd,
	Spec
}
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
import se.lu.nateko.cp.meta.services.sparql.magic.{CpIndex, IndexHandler}
import se.lu.nateko.cp.meta.utils.rdf4j.Loading

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using
import org.eclipse.rdf4j.model.vocabulary.RDF
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import se.lu.nateko.cp.meta.utils.rdf4j.accessEagerly

class SerializationTests extends AsyncFunSpec {

	given EnvriConfigs = MetaCoreConfig.default.envriConfigs

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
		IndexHandler.storeToStream(idx, os).map { _ => os.toByteArray }
	}

	def loadFromBytes(arr: Array[Byte])(using Kryo): Future[IndexData] = {
		val is = ByteArrayInputStream(arr)
		IndexHandler.restoreFromStream(is)
	}

	def roundTrip(sail: Sail)(using Kryo) = // : Future[(CpIndex, CpIndex)] =
		for (
			idx <- Future(CpIndex(sail, Future.never, 5));
			arr <- saveToBytes(idx);
			data <- loadFromBytes(arr)
		) yield idx -> CpIndex(sail, Future.never, data)

	def smallRepo = Loading.fromResource("/rdf/someDobjsAndSpecs.ttl", "http://test.icos-cp.eu/blabla", RDFFormat.TURTLE)

	describe("Small index created, object deleted, leaving orphan data type"):
		it("successfully performs serialization/deserialization round trip"):
			given Kryo = IndexHandler.makeKryo
			val repo = smallRepo
			val idx = CpIndex(repo.getSail, Future.never, 5)
			val cpmeta = CpmetaVocab(repo.getValueFactory)
			val idxHandler = IndexHandler(DummyScheduler)
			val listener = idxHandler.getListener(repo.getSail, cpmeta, idx, Future.never)

			Using(repo.getSail.getConnection): baseconn =>
				val conn = baseconn.asInstanceOf[NotifyingSailConnection]
				conn.addConnectionListener(listener)
				// deleting an object, leaving one data type "an orphan"
				val delObj = repo.getValueFactory.createIRI("https://meta.icos-cp.eu/objects/hoidzqcaqmCU3mOZ435r2crG")
				conn.begin()
				conn.removeStatements(delObj, null, null)
				conn.commit()
			repo.shutDown()

			for arr <- saveToBytes(idx); _ <- loadFromBytes(arr)
			yield succeed

	describe("CpIndex created from test RDF in a turtle file, and round-tripped") {
		val repo = smallRepo
		given Kryo = IndexHandler.makeKryo
		val idxFut = roundTrip(repo.getSail).andThen { _ =>
			repo.shutDown()
		}
		val toData: CpIndex => IndexData = _.serializableData.asInstanceOf[IndexData]
		val droughtObjHash = Sha256Sum.fromBase64Url("8KgcKLxNMRE-AtEEEFH6e_yd").get
		val etcObjHash = Sha256Sum.fromBase64Url("hoidzqcaqmCU3mOZ435r2crG").get

		def origAndCopy[T](title: String, expectation: T)(theTest: CpIndex => T) = it(title) {
			idxFut.map { (idx0, idx1) =>
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

		origAndCopy("should correctly filter by submission date", 1) { idx =>
			val filter = ContFilter(SubmissionEnd, MinFilter(Instant.parse("2019-06-01T00:00:00Z").toEpochMilli, false))
			val fetch = DataObjectFetch(filter, None, 0)
			val objs = idx.fetch(fetch).toSeq
			objs.size
		}

		origAndCopy("both objects must be marked as initialized", Array(0, 1)) {
			toData.andThen(_.initOk.toArray)
		}

		origAndCopy("contain expected hashsums", Set(etcObjHash, droughtObjHash)) {
			toData.andThen(_.objs.map(_.hash).toSet)
		}

		origAndCopy("correctly filters by station", 1) { idx =>
			val fiSii = Values.iri("http://meta.icos-cp.eu/resources/stations/ES_FI-Sii")
			val filter = CategFilter(Station, Seq(Some(fiSii)))
			idx.fetch(DataObjectFetch(filter, None, 0)).toSeq.size
		}

		origAndCopy("correctly fetches dobjs", 2) { idx =>
			val res =
				idx.fetch(DataObjectFetch(And(List(Exists(FileName), Exists(SubmissionEnd))), Some(SortBy(SubmissionEnd, true)), 0)).toSeq
			res.size
		}

		origAndCopy("has two stations", 2)(
			toData.andThen(_.categoryKeys(Station).size)
		)
	}

	describe("Specs associated with empty object bitmaps") {
		it("are retained during serialization") {
			val repo = Loading.emptyInMemory
			val cpmeta = CpmetaVocab(repo.getValueFactory)
			val spec = repo.getValueFactory.createIRI("test:spec")
			val empty_spec = repo.getValueFactory.createIRI("test:empty_spec")
			// Must be a valid data object
			val dataObject = repo.getValueFactory.createIRI("https://meta.icos-cp.eu/objects/oAzNtfjXddcnG_irI8fJT7W6")
			val index_data = IndexData(5)()

			// Insert one lonely spec, and one with an associated data object
			repo.getSail().accessEagerly {
				index_data.processUpdate(Rdf4jStatement(empty_spec, RDF.TYPE, cpmeta.dataObjectSpecClass), true, cpmeta)
				index_data.processUpdate(Rdf4jStatement(dataObject, cpmeta.hasObjectSpec, spec), true, cpmeta)
			}

			// Don't need the repo anymore
			repo.shutDown()

			// Serialize index_data through CpIndex in a bit of a roundabout way...
			val index = CpIndex(repo.getSail, Future.never, index_data)

			// Make sure we start out with the expected spec keys
			val original_keys = index_data.categoryKeys(Spec)
			assert(original_keys.toSet == Set(empty_spec, spec))

			given Kryo = IndexHandler.makeKryo
			for serialized <- saveToBytes(index); loaded <- loadFromBytes(serialized)
			yield {
				// After deserialization, both specs should still be known
				assert(loaded.categoryKeys(Spec) == original_keys)
			}
		}
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
