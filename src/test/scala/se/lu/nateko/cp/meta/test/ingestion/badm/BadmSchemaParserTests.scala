package se.lu.nateko.cp.meta.test.ingestion.badm

import org.scalatest.FunSpec

class BadmSchemaParserTests extends FunSpec{

	describe("parseSchemaFromCsv"){
		it("Parses the test BADM schema successfully"){
			val schema = BadmTestHelper.getSchema
			//schema.vocabs.keys.foreach(println)
			assert(schema.size === 51)
			assert(schema.values.filter(_.hasVocab).size === 20)
		}
	}
}
