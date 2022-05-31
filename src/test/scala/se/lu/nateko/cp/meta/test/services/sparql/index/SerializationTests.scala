package se.lu.nateko.cp.meta.test.services.sparql.index

import akka.event.NoLogging
import org.eclipse.rdf4j.model.util.Values
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.sail.Sail
import org.scalatest.compatible.Assertion
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.services.sparql.index.All
import se.lu.nateko.cp.meta.services.sparql.index.CategFilter
import se.lu.nateko.cp.meta.services.sparql.index.ContFilter
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap.MinFilter
import se.lu.nateko.cp.meta.services.sparql.index.Property
import se.lu.nateko.cp.meta.services.sparql.index.Station
import se.lu.nateko.cp.meta.services.sparql.index.SubmissionEnd
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.IndexData
import se.lu.nateko.cp.meta.services.sparql.magic.IndexHandler
import se.lu.nateko.cp.meta.utils.rdf4j.Loading

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import scala.concurrent.Future
import scala.util.Using

class SerializationTests extends AsyncFunSpec{

	def saveToBytes(idx: CpIndex): Future[Array[Byte]] = {
		val os = ByteArrayOutputStream()
		IndexHandler.storeToStream(idx, os).map{_ => os.toByteArray}
	}

	def loadFromBytes(arr: Array[Byte]): Future[IndexData] = {
		val is = ByteArrayInputStream(arr)
		IndexHandler.restoreFromStream(is)
	}

	def roundTrip(sail: Sail): Future[(CpIndex, CpIndex)] =
		for(
			idx <- Future(CpIndex(sail, 5)(NoLogging));
			arr <- saveToBytes(idx);
			data <- loadFromBytes(arr)
		) yield idx -> CpIndex(sail, data)(NoLogging)

	describe("CpIndex created from test RDF in a turtle file, and round-tripped"){
		val repo = Loading.fromResource("/rdf/someDobjsAndSpecs.ttl", "http://test.icos-cp.eu/blabla", RDFFormat.TURTLE)
		val idxFut: Future[(CpIndex, CpIndex)] = roundTrip(repo.getSail).andThen{_ =>
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

		origAndCopy("has two stations", 2)(
			toData.andThen(_.categMaps(Station).size)
		)
	}
}
