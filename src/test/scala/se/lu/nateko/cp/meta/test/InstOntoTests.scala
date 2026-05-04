package se.lu.nateko.cp.meta.test

import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.query.parser.ParsedTupleQuery
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.onto.{InstOnto, Onto}
import scala.jdk.CollectionConverters.SetHasAsScala

import java.net.URI

class InstOntoTests extends AnyFunSpec{

	val onto = new Onto(TestConfig.owlOnto)
	val instOnto = new InstOnto(TestConfig.instServer, onto)
	val stationClassUri = new URI(TestConfig.ontUri + "Station")
	private def parseTupleQuery(query: String): ParsedTupleQuery =
		(new SPARQLParser).parseQuery(query, null) match
			case parsed: ParsedTupleQuery => parsed
			case other => fail(s"Expected ParsedTupleQuery, got ${other.getClass.getSimpleName}")

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
			val parsed = parseTupleQuery(query)
			val bindingNames = parsed.getTupleExpr.nn.getBindingNames.nn.asScala

			assert(bindingNames.contains("s"))
			assert(bindingNames.contains("label"))
			assert(bindingNames.contains("comment"))
			assert(bindingNames.contains("seeAlso"))
			assert(query.contains(s"?s rdf:type <$stationClassUri> ."))
			assert(query.contains("ORDER BY ?s"))
		}

		it("includes subject prefix filter when provided"){
			val prefix = new URI("http://meta.icos-cp.eu/")
			val query = instOnto.getIndividualsSparql(stationClassUri, Some(prefix))
			parseTupleQuery(query)

			assert(query.contains(s"""FILTER(STRSTARTS(STR(?s), "$prefix"))"""))
		}

		it("omits subject prefix filter when not provided"){
			val query = instOnto.getIndividualsSparql(stationClassUri, None)
			parseTupleQuery(query)

			assert(!query.contains("STRSTARTS(STR(?s),"))
		}
	}
}
