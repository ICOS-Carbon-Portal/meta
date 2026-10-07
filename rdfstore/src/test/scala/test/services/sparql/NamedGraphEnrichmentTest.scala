package se.lu.nateko.cp.meta.test.services.sparql

import scala.language.unsafeNulls

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.{CpmetaVocab, Rdf4jSparqlRunner}
import se.lu.nateko.cp.meta.test.services.sparql.regression.TestDb
import se.lu.nateko.cp.meta.utils.rdf4j.accessEagerly

import scala.jdk.CollectionConverters.IteratorHasAsScala

@tags.DbTest
class NamedGraphEnrichmentTest extends AnyFunSpec:

	private lazy val db = TestDb()
	private lazy val meta = CpmetaVocab(db.repo.getValueFactory)
	private val obj = "https://meta.icos-cp.eu/objects/XX3nZE3l0ODO9QA-T9gqI0GU"
	private val icosLicence = "http://meta.icos-cp.eu/ontologies/cpmeta/icosLicence"

	private def predicateObjects(query: String): Seq[(String, String)] =
		val res = Rdf4jSparqlRunner(db.repo).evaluateTupleQuery(query)
		try res.map(bs => bs.getValue("p").stringValue -> bs.getValue("o").stringValue).toIndexedSeq
		finally res.close()

	private def derivedPredicates = Set(meta.hasBiblioInfo, meta.hasCitationString).map(_.stringValue)

	describe("derived metadata in SPARQL results"):

		it("is added to the statements of a data object queried without a named graph"):
			val pos = predicateObjects(s"select ?p ?o where{ <$obj> ?p ?o }")
			assert(pos.exists((p, _) => derivedPredicates.contains(p)))
			assert(pos.contains(meta.dcterms.license.stringValue -> icosLicence))

		it("is not added to the statements of a data object queried in named graphs"):
			val pos = predicateObjects(s"select ?p ?o where{ graph ?g { <$obj> ?p ?o } }")
			assert(pos.nonEmpty)
			assert(!pos.exists((p, _) => derivedPredicates.contains(p)))
			assert(!pos.contains(meta.dcterms.license.stringValue -> icosLicence))

		it("leaves the stored statements of a data object in named graphs intact"):
			// the connection adds derived metadata as well, but outside of any named graph
			val stored = db.repo.accessEagerly(
				_.getStatements(db.repo.getValueFactory.createIRI(obj), null, null, false).iterator.asScala
					.filter(_.getContext != null)
					.map(st => st.getPredicate.stringValue -> st.getObject.stringValue).toSet
			)
			val pos = predicateObjects(s"select ?p ?o where{ graph ?g { <$obj> ?p ?o } }").toSet
			assert(pos == stored)
