package se.lu.nateko.cp.meta.test.services.sparql.magic.fusion

import org.scalatest.funspec.AnyFunSpec
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.query.algebra.TupleExpr

import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.magic.fusion._
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap.IntervalFilter
import se.lu.nateko.cp.meta.services.sparql.index._

class DofPatternSearchTests extends AnyFunSpec{
	private val meta = new CpmetaVocab(new MemValueFactory)
	private val dofps = new DofPatternSearch(meta)
	private val parser = new SPARQLParser

	private def parseQuery(q: String): TupleExpr = parser.parseQuery(q, "http://dummy.org").getTupleExpr

	describe("Pattern detection"){

		def getPattern(queryStr: String): (TupleExpr, DofPattern) = {
			val query = parseQuery(queryStr)
			query -> dofps.find(query)
		}

		describe("Spec, submitter, station inline selections and temp filters"){


			it("Pattern detection does not crash"){
				lazy val (query @ _, patt) = getPattern(TestQueries.fetchDobjListFromNewIndex)
				println(patt)
			}

			
		}

	}


}

