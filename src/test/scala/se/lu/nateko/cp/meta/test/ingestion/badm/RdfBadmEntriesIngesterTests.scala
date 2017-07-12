package se.lu.nateko.cp.meta.test.ingestion.badm

import org.scalatest.AsyncFunSpec
import se.lu.nateko.cp.meta.ingestion.badm.RdfBadmEntriesIngester
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import se.lu.nateko.cp.meta.ingestion.badm.Parser._
import scala.concurrent.Future

class RdfBadmEntriesIngesterTests extends AsyncFunSpec{

	it("Successfully produces RDF statements from the test BadmSchema"){

		val schema = Future.successful(BadmTestHelper.getSchema)

		val badmSource = BadmTestHelper.getBadmSource

		val entries = Future.successful(parseEntriesFromCsv(badmSource))
		
		val ingester = new RdfBadmEntriesIngester(entries, schema)

		val factory: ValueFactory = new MemValueFactory

		ingester.getStatements(factory).map{statements =>
			assert(statements.length === 423)
		}
	}
}
