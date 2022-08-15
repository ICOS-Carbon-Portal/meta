package se.lu.nateko.cp.meta.test

import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.util.Literals
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.query.BindingSet
import org.scalatest.BeforeAndAfterAll
import org.scalatest.compatible.Assertion
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.test.services.sparql.regression.TestDb
import se.lu.nateko.cp.meta.test.services.sparql.regression.TestQueries
import se.lu.nateko.cp.meta.utils.rdf4j.createLiteral

import java.io._
import java.util.UUID.randomUUID
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.HashSet
import scala.concurrent.Future
import scala.concurrent.Future.apply
import scala.jdk.CollectionConverters.IterableHasAsScala

import collection.mutable.ListBuffer

class QueryTests extends AsyncFunSpec with BeforeAndAfterAll {

	lazy val db: TestDb = new TestDb
	type Rows = Future[IndexedSeq[BindingSet]]

	override def afterAll() = {
		db.cleanup()
	}

	def queryDb(query: String) = {
		db.runSparql(query)
	}

	def sortRows(colToSortBy: String, rows: Future[CloseableIterator[BindingSet]]) = {	
		rows map { r =>
			r.toIndexedSeq.sortBy(_.getValue(colToSortBy).stringValue)
		}
	}

	def checkSampleRow(sampleIndex: Int)(sampleMaker: ValueFactory => Map[String, Value])(using rows: Rows) =
		qtest("should return correct sample row"){f =>
			for (r <- rows) yield {
				val sampleRow = r(sampleIndex).asScala.map(b => b.getName -> b.getValue).toMap
				assert(sampleRow === sampleMaker(f))
			}
		}

	def checkNbrOfRows(nbr: Int)(using rows: Rows) = {
		it(s"should return $nbr rows") {
			rows map { r => assert(r.size === nbr) }
		}
	}

	def qtest(tip: String)(test: ValueFactory => Future[Assertion]) = it(tip)(db.repo.flatMap(r => test(r.getValueFactory)))

	describe("Data type basics") {
		// val rows = queryDb(TestQueries.dataTypeBasics).andThen{
		// 	case Success(rows) => sortRows("spec", rows)
		// 	case Failure(err) => fail()
		// }

		given rows: Rows = sortRows("spec", queryDb(TestQueries.dataTypeBasics))

		checkNbrOfRows(76)

		checkSampleRow(sampleIndex=0)(f => 
			Map(
			"level" -> f.createLiteral("3", XSD.INTEGER),
			"format" -> f.createIRI("http://meta.icos-cp.eu/ontologies/cpmeta/netcdf"),
			"project" -> f.createIRI("http://meta.icos-cp.eu/resources/projects/icos"),
			"type" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/anthropogenicEmissionModelResults"),
			"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/anthropogenicEmissionModelResults"),
			"theme" -> f.createIRI("http://meta.icos-cp.eu/resources/themes/atmosphere"),
			"dataset" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/anthropogenicEmissionModelResultsDataset")
			)
		)
	}

	describe("Variable metadata and relation to data types") {
		given rows: Rows = sortRows("spec", queryDb(TestQueries.variables))

		checkNbrOfRows(362)

		checkSampleRow(sampleIndex=10)(f => 
			Map(
			"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcC14L2DataObject"),
			"variable" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/timeStampColumn"),
			"varTitle" -> f.createLiteral("TIMESTAMP"),
			"valType" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/timeStamp"),
			"quantityUnit" -> f.createLiteral("(not applicable)")
			)
		)
	}

	describe("Statistics of data object origins") {
		given rows: Rows = sortRows("spec", queryDb(TestQueries.dataObjOriginStats))

		checkNbrOfRows(797)

		checkSampleRow(sampleIndex=100)(f => 
			Map(
			"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcCo2L2DataObject"),
			"countryCode" -> f.createLiteral("DE"),
			"submitter" -> f.createIRI("http://meta.icos-cp.eu/resources/organizations/ATC"),
			"count" -> f.createLiteral("2", XSD.INT),
			"station" -> f.createIRI("http://meta.icos-cp.eu/resources/stations/AS_SSL"),
			"stationclass" -> f.createLiteral("ICOS")
			)
		)
	}

	describe("Data object details") {
		given rows: Rows = sortRows("dobj", queryDb(TestQueries.detailedDataObjInfo))

		checkNbrOfRows(20)

		checkSampleRow(sampleIndex=10)(f => 
			Map(
			"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/bfgU0FHcHBw_te8v4J7WSswp"),
			"dois" -> f.createLiteral("")
			)
		)
	}

	describe("Labels for metadata entities") {
		given rows: Rows = sortRows("uri", queryDb(TestQueries.labels))

		checkNbrOfRows(1078)

		checkSampleRow(sampleIndex=500)(f => 
			Map(
			"label" -> f.createLiteral("ocean"), 
			"uri" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/ocean")
			)
		)
	}

	describe("Keywords associated with data types") {
		given rows: Rows = sortRows("spec", queryDb(TestQueries.keywords))

		checkNbrOfRows(94)

		checkSampleRow(sampleIndex=50)(f =>
			Map(
				"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/etcL2Fluxes"),
				"keywords" -> f.createLiteral("ICOS")
			)
		)
	}
}
