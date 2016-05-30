package se.lu.nateko.cp.meta.test.ingestion.badm

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.ingestion.badm.RdfBadmEntriesIngester
import org.openrdf.model.ValueFactory
import org.openrdf.sail.memory.model.MemValueFactory
import se.lu.nateko.cp.meta.ingestion.badm.Parser._

class RdfBadmEntriesIngesterTests extends FunSpec{

	it("Successfully produces RDF statements from the test BadmSchema"){
		val schema = BadmTestHelper.getSchema
		val badmSource = BadmTestHelper.getBadmSource
		val entries = parseEntriesFromCsv(badmSource)
		val ingester = new RdfBadmEntriesIngester(entries, schema)
		val factory: ValueFactory = new MemValueFactory

		val statements = ingester.getStatements(factory).toIndexedSeq
		assert(statements.length === 423)
		//statements.foreach(println)
	}
}
