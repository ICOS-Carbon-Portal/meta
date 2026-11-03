package se.lu.nateko.cp.meta.test.services.upload

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import se.lu.nateko.cp.meta.{DataObjectDto, DocObjectDto, ReferencesDto, StationTimeSeriesDto}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs}
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}

import java.net.URI

/** Each test owns its store; tests never depend on mutations made by another test. */
object UploadTestFixture:
	given Envri = Envri.ICOS
	given EnvriConfigs = Map(Envri.ICOS -> EnvriConfig(
		authHost = "cpauth.icos-cp.eu", dataHost = "data.icos-cp.eu", metaHost = "meta.icos-cp.eu",
		dataItemPrefix = URI("https://meta.icos-cp.eu/"),
		metaItemPrefix = URI("https://meta.icos-cp.eu/"), defaultTimezoneId = "UTC"
	))

	val hash = Sha256Sum.fromHex("01" * 32).get
	val previous = Sha256Sum.fromHex("02" * 32).get
	val other = Sha256Sum.fromHex("03" * 32).get
	val organization = URI("https://meta.icos-cp.eu/organizations/test")
	val references = ReferencesDto(None, None, None, None, None, None)
	val document = DocObjectDto(hash, "test", "report.pdf", None, None, Nil, None, None, None)

	val dataObject = DataObjectDto(hash, "test", URI("https://example.org/spec"), "data.csv",
		Right(StationTimeSeriesDto(organization, None, None, None, None, None, None, None, None, None)),
		None, None, None
	)

	class Store extends AutoCloseable:
		val repo = new SailRepository(new MemoryStore)
		repo.init()
		val factory = repo.getValueFactory
		val vocab = CpVocab(factory)
		val meta = CpmetaVocab(factory)
		val server = Rdf4jInstanceServer(repo, "https://meta.icos-cp.eu/tests/upload/")
		/** Scenario graph names select fixtures; the server supplies the actual write context. */
		def fixture(file: String, scenario: String): Set[Statement] =
			val resource = s"/upload/$file.trig"
			val stream = Option(getClass.getResourceAsStream(resource)).getOrElse:
				throw new IllegalArgumentException(s"Missing fixture $resource")
			Using.resource(stream): input =>
				val model = Rio.parse(input, "", RDFFormat.TRIG)
				val graph = factory.createIRI(s"urn:upload-test:$scenario")
				val statements = model.filter(null, null, null, graph).asScala.map: st =>
					factory.createStatement(st.getSubject, st.getPredicate, st.getObject)
				.toSet
				require(statements.nonEmpty, s"Missing or empty scenario $scenario in $resource")
				statements

		def loadFixture(file: String, scenario: String): Unit =
			server.addAll(fixture(file, scenario).toSeq).get
		def close(): Unit = repo.shutDown()

	def withStore[T](test: Store => T): T =
		val store = new Store
		try test(store)
		finally store.close()
