package se.lu.nateko.cp.meta.test.api

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.{CustomVocab, UriId}
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory

class CustomVocabTests extends AnyFunSpec{

	private object Vocab extends CustomVocab{
		given bup: BaseUriProvider = makeUriProvider("http://test.icos-cp.eu/ontologies/test/")
		val factory: ValueFactory = new MemValueFactory
		def encode(s: String) = CustomVocab.urlEncode(s)
	}

	describe("getRelative and getRelativeRaw"){
		import Vocab.bup

		it("Supports non-URL-friendly characters"){
			val nonUrl = "WITH SPACE"
			val rel = Vocab.getRelative(UriId.escaped(nonUrl))
			assert(rel.getLocalName === Vocab.encode(nonUrl))
		}

		it("works correctly if client code does URLEncoding itself"){
			val email = "fname lname@web-site.com"
			val extraEncoded = Vocab.getRelativeRaw(Vocab.encode(email))
			val simple = Vocab.getRelative(UriId.escaped(email))
			assert(simple === extraEncoded)
		}
	}

	describe("equality of URIs"){

		it("Works as expected even for CustomVocabs with different ValueFactories"){
			object tempVocab extends CustomVocab{
				given BaseUriProvider = makeUriProvider(Vocab.bup.baseUri)
				val factory: ValueFactory = new MemValueFactory
				val bebe = getRelativeRaw("bebe")
			}

			assert(Vocab.getRelativeRaw("bebe") === tempVocab.bebe)
		}
	}
}
