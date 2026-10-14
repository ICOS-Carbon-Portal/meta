package se.lu.nateko.cp.meta.services.derived

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.ConfigLoader
import se.lu.nateko.cp.meta.services.citation.{CitationProvider, CitationProviderConfig}
import se.lu.nateko.cp.meta.test.services.sparql.regression.CitationClientDummy

import scala.util.Using

class DerivedMetadataServiceTest extends AnyFunSpec with BeforeAndAfterAll:

	private given system: ActorSystem = ActorSystem("DerivedMetadataServiceTest")

	private val repo =
		val repo = SailRepository(MemoryStore())
		repo.init()
		Using.resources(getClass.getResourceAsStream("/linkeddata/duplicate-email-staff.trig"), repo.getConnection()):
			(stream, conn) => conn.add(stream, "", RDFFormat.TRIG)
		repo

	override def afterAll(): Unit =
		repo.shutDown()
		system.terminate()

	describe("derived metadata"):

		it("reports a warning when duplicate metadata entries exist"):
			val conf = ConfigLoader.default
			val citer = new CitationProvider(
				repo, _ => CitationClientDummy, conf.core,
				CitationProviderConfig.getLenses(conf),
				CitationProviderConfig.pidFactory(conf)
			)
			val timeSeriesObject = repo.getValueFactory.createIRI("https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB")

			// the staff member is read only for the authors of the object's citation
			val result = DerivedMetadataService(citer).resolve(timeSeriesObject)
			assert(result.status === "ready")
			val metadata = result.metadata.getOrElse(fail("no derived metadata"))
			assert(metadata.references.title === Some("Test time series from Test station"))
			assert(metadata.warnings.distinct === Seq(
				"Expected at most one values of property http://meta.icos-cp.eu/ontologies/cpmeta/hasEmail " +
					"for resource http://meta.icos-cp.eu/resources/people/Twomail_Person, but got 2"
			))

end DerivedMetadataServiceTest
