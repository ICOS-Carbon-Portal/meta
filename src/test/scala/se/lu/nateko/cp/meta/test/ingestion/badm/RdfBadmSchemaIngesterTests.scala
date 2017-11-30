package se.lu.nateko.cp.meta.test.ingestion.badm

import org.scalatest.AsyncFunSpec
import se.lu.nateko.cp.meta.ingestion.badm.RdfBadmSchemaIngester
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import scala.concurrent.Future
import se.lu.nateko.cp.meta.test.TestConfig

class RdfBadmSchemaIngesterTests extends AsyncFunSpec{
	import TestConfig.envriConfs

	it("Successfully produces RDF statements from the test BadmSchema"){
		val schema = Future.successful(BadmTestHelper.getSchema)
		val ingester = new RdfBadmSchemaIngester(schema)
		val factory: ValueFactory = new MemValueFactory

		ingester.getStatements(factory).map{statements =>
			assert(statements.length === 405)
		}
	}
}
