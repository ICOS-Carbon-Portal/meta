package se.lu.nateko.cp.meta.test.api

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.api.CustomVocab
import org.openrdf.model.ValueFactory
import org.openrdf.sail.memory.model.MemValueFactory
import java.net.URLEncoder

class CustomVocabTests extends FunSpec{

	private[this] object Vocab extends CustomVocab{
		val baseUri = "http://test.icos-cp.eu/ontologies/test/"
		val factory: ValueFactory = new MemValueFactory
	}

	describe("getRelative and getRelativeRaw"){
		it("Supports non-URL-friendly characters"){
			val nonUrl = "WITH SPACE"
			val rel = Vocab.getRelative(nonUrl)
			assert(rel.getLocalName === URLEncoder.encode(nonUrl, "UTF-8"))
		}

		it("works correctly if client code does URLEncoding itself"){
			val email = "fname.lname@web-site.com"
			val extraEncoded = Vocab.getRelativeRaw(URLEncoder.encode(email, "UTF-8"))
			val simple = Vocab.getRelative(email)
			assert(simple === extraEncoded)
		}
	}
}
