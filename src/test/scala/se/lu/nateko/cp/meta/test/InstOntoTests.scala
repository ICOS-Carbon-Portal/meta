package se.lu.nateko.cp.meta.test

import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.onto.{InstOnto, Onto}

import java.net.URI

class InstOntoTests extends AnyFunSpec{

	val onto = new Onto(TestConfig.owlOnto)
	val instOnto = new InstOnto(TestConfig.instServer, onto)
	val stationClassUri = new URI(TestConfig.ontUri + "Station")

	describe("getIndividual"){

		it("correctly constructs display name for Membership individual"){
			val uri = new URI(TestConfig.instOntUri + "atcDirector")
			val indInfo = instOnto.getIndividual(uri)
			
			assert(indInfo.resource.displayName === "Director at Atmosphere Thematic Centre")
		}
		
	}

	describe("getRangeValues"){

		it("lists LatLonBox instances as range values of hasSpatialCoverage (on Station class instances)"){
			val stationClass = new URI(TestConfig.ontUri + "Station")
			val spatialCovProp = new URI(TestConfig.ontUri + "hasSpatialCoverage")
			val globalBox = new URI(TestConfig.instOntUri + "globalLatLonBox")
			val range = instOnto.getRangeValues(stationClass, spatialCovProp)
			assert(range.map(_.uri).contains(globalBox))
		}
	}

	describe("getIndividualsSparql"){

		it("includes basic query structure and common projected properties"){
			val query = instOnto.getIndividualsSparql(stationClassUri, None)

			assert(query.contains("SELECT ?s"))
			assert(query.contains(s"?s rdf:type <$stationClassUri> ."))
			assert(query.contains("ORDER BY ?s"))
			assert(query.contains("rdfs:label"))
			assert(query.contains("rdfs:comment"))
		}

		it("includes subject prefix filter when provided"){
			val prefix = "http://meta.icos-cp.eu/"
			val query = instOnto.getIndividualsSparql(stationClassUri, Some(prefix))

			assert(query.contains(s"FILTER(STRSTARTS(STR(?s), \"$prefix\"))"))
		}

		it("omits subject prefix filter when not provided"){
			val query = instOnto.getIndividualsSparql(stationClassUri, None)

			assert(!query.contains("STRSTARTS(STR(?s),"))
		}

		it("escapes quotes and backslashes in subject prefix"){
			val prefix = "http://meta.icos-cp.eu/path\\with\\slash/\"quoted\""
			val query = instOnto.getIndividualsSparql(stationClassUri, Some(prefix))

			assert(query.contains("path\\\\with\\\\slash"))
			assert(query.contains("\\\"quoted\\\""))
		}

		it("produces a syntactically valid SPARQL query"){
			val query = instOnto.getIndividualsSparql(stationClassUri, Some("http://meta.icos-cp.eu/"))

			(new SPARQLParser).parseQuery(query, null)
			assert(true)
		}
	}
}
