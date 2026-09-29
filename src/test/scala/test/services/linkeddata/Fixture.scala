package se.lu.nateko.cp.meta.test.services.linkeddata

import scala.language.unsafeNulls

import akka.http.scaladsl.model.Uri
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.sail.memory.MemoryStore
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

import scala.util.Using

/**
 * A small in-memory metadata store with sample data objects, collections, stations, instruments,
 * people and organizations, along with the URIs of its entries.
 */
object Fixture {

	/** A new in-memory repository with the sample metadata; the caller must shut it down. */
	def createRepo(): Repository = {
		val repo = SailRepository(MemoryStore())
		repo.init()
		Using.resources(
			getClass.getResourceAsStream("/linkeddata/linked-data-fixture.trig"),
			repo.getConnection()
		) { (stream, conn) =>
			conn.add(stream, "", RDFFormat.TRIG)
		}
		repo
	}

	private val factory = SimpleValueFactory.getInstance()

	val resource = factory.createIRI("http://meta.icos-cp.eu/resources/test/serializer_test")
	val referringResource = factory.createIRI("http://meta.icos-cp.eu/resources/test/serializer_test_referrer")
	val predicate = factory.createIRI("http://example.org/refersTo")
	val resourceUri = Uri(resource.stringValue)

	val missingObjectHash = Sha256Sum.fromBytes(Array.fill(18)(0.toByte)).get
	val missingObjectUri = Uri(s"https://meta.icos-cp.eu/objects/${missingObjectHash.id}")

	//the hashes of the objects and collections, as the loader addresses them
	private def hash(seed: Byte) = Sha256Sum.fromBytes(Array.fill(18)(seed)).get
	val timeSeriesHash = hash(1)
	val documentHash = hash(2)
	val testCollectionHash = hash(3)

	val timeSeriesObject = Uri("https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB")
	val versionedObject = Uri("https://meta.icos-cp.eu/objects/BQUFBQUFBQUFBQUFBQUFBQUF")
	val spatialObject = Uri("https://meta.icos-cp.eu/objects/EhISEhISEhISEhISEhISEhIS")
	val documentObject = Uri("https://meta.icos-cp.eu/objects/AgICAgICAgICAgICAgICAgIC")
	val testCollection = Uri("https://meta.icos-cp.eu/collections/AwMDAwMDAwMDAwMDAwMDAwMD")
	val nestedCollection = Uri("https://meta.icos-cp.eu/collections/DAwMDAwMDAwMDAwMDAwMDAwM")
	val icosStation = Uri("http://meta.icos-cp.eu/resources/stations/TST")
	val ecosystemStation = Uri("http://meta.icos-cp.eu/resources/stations/ES_TST")
	val sitesStation = Uri("https://meta.fieldsites.se/resources/stations/Testsjon")
	val organization = Uri("http://meta.icos-cp.eu/resources/organizations/CP")
	val instrument = Uri("http://meta.icos-cp.eu/resources/instruments/TST_1")
	val instrumentComponent = Uri("http://meta.icos-cp.eu/resources/instruments/TST_2")
	val person = Uri("http://meta.icos-cp.eu/resources/people/Test_Person")
	val objectSpec = Uri("http://meta.icos-cp.eu/resources/cpmeta/testTimeSeries")
	val dataTheme = Uri("http://meta.icos-cp.eu/resources/themes/atmosphere")
}
