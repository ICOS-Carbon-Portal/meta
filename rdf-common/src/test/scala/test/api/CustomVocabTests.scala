package se.lu.nateko.cp.meta.test.api

import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.{CustomVocab, UriId}

import java.net.URI

class CustomVocabTests extends AnyFunSpec{

	private object Vocab extends CustomVocab{
		given bup: BaseUriProvider = makeUriProvider("http://test.icos-cp.eu/ontologies/test/")
		val factory: ValueFactory = new MemValueFactory
		def encode(s: String) = CustomVocab.urlEncode(s)
	}

	describe("UriId"){
		import Vocab.bup

		it("preserves a URL-safe string when round-tripping through a URI or IRI"){
			val urlSafe = "some%20id%2Fwith%25escapes-._~"
			val id = UriId(urlSafe)
			val iri = Vocab.getRelative(id)

			assert(id.urlSafeString === urlSafe)
			assert(id.toString === urlSafe)
			assert(UriId(new URI(iri.stringValue)).urlSafeString === urlSafe)
			assert(UriId(iri).urlSafeString === urlSafe)
		}
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
