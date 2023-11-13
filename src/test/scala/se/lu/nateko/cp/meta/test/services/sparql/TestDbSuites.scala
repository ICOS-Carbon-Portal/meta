package se.lu.nateko.cp.meta.test.services.sparql

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import se.lu.nateko.cp.meta.test.services.sparql.regression.QueryTests
import se.lu.nateko.cp.meta.test.services.sparql.regression.TestDb


trait TestDbFixture:
	val db: TestDb = TestDb.instance

class TestDbSuites extends Suite with BeforeAndAfterAll:
	override protected def afterAll(): Unit =
		TestDb.instance.cleanup()
		super.afterAll()

	override def nestedSuites = Vector(new QueryTests, new SparqlRouteTests)
