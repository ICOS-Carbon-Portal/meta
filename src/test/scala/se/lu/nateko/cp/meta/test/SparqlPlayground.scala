package se.lu.nateko.cp.meta.test

import org.eclipse.rdf4j.query.parser.ParsedQuery
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser

object SparqlPlayground extends App {

	val query: String = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	|select * where{
	|	[] cpmeta:hasStatProps [
	|		cpmeta:hasStatCount ?count;
	|		cpmeta:hasStatStation ?station
	|	].
	|}""".stripMargin

	val parsed: ParsedQuery = new SPARQLParser().parseQuery(query, null)

	println(parsed.getTupleExpr.toString)
}