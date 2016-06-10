package se.lu.nateko.cp.meta.test.api

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.api.CustomVocab
import org.openrdf.model.ValueFactory
import org.openrdf.sail.memory.model.MemValueFactory

class CustomVocabTests extends FunSpec{

	private[this] object Vocab extends CustomVocab{
		val baseUri = "http://test.icos-cp.eu/ontologies/test/"
		val factory: ValueFactory = new MemValueFactory
		def encode(s: String) = urlEncode(s)
	}

	describe("getRelative and getRelativeRaw"){
		it("Supports non-URL-friendly characters"){
			val nonUrl = "WITH SPACE"
			val rel = Vocab.getRelative(nonUrl)
			assert(rel.getLocalName === Vocab.encode(nonUrl))
		}

		it("works correctly if client code does URLEncoding itself"){
			val email = "fname lname@web-site.com"
			val extraEncoded = Vocab.getRelativeRaw(Vocab.encode(email))
			val simple = Vocab.getRelative(email)
			assert(simple === extraEncoded)
		}
	}
}
