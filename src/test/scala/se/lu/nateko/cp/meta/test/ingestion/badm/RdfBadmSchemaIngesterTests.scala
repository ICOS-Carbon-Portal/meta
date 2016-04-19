package se.lu.nateko.cp.meta.test.ingestion.badm

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.ingestion.badm.RdfBadmSchemaIngester
import org.openrdf.model.ValueFactory
import org.openrdf.sail.memory.model.MemValueFactory

class RdfBadmSchemaIngesterTests extends FunSpec{

	it("Successfully produces RDF statements from the test BadmSchema"){
		val schema = BadmTestHelper.getSchema
		val ingester = new RdfBadmSchemaIngester(schema)
		val factory: ValueFactory = new MemValueFactory

		val statements = ingester.getStatements(factory).toIndexedSeq
		assert(statements.length === 712)
		//statements.foreach(println)
	}
}
