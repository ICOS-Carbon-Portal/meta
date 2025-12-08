package se.lu.nateko.cp.meta.test.prototype

import scala.language.unsafeNulls

import java.nio.file.Files
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import se.lu.nateko.cp.meta.prototype.ntriples.SqlSail
import scala.jdk.CollectionConverters.*

class NTriplesSailTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

	var tempDir: java.io.File = _

	override def beforeEach(): Unit = {
		tempDir = Files.createTempDirectory("ntriples-sail-test").toFile
	}

	override def afterEach(): Unit = {
		// Clean up temp directory
		def deleteRecursively(file: java.io.File): Unit = {
			if (file.isDirectory) {
				file.listFiles().foreach(deleteRecursively)
			}
			file.delete()
		}
		if (tempDir != null && tempDir.exists()) {
			deleteRecursively(tempDir)
		}
	}

	"NTriplesSail" should "initialize and shutdown without errors" in {
		val sail = new SqlSail(tempDir)
		val repo = new SailRepository(sail)
		repo.init()
		repo.shutDown()
	}

	it should "add and query statements" in {
		val sail = new SqlSail(tempDir)
		val repo = new SailRepository(sail)
		repo.init()

		try {
			val conn = repo.getConnection
			try {
				conn.begin()
				val vf = conn.getValueFactory
				val s = vf.createIRI("http://example.org/subject")
				val p = vf.createIRI("http://example.org/predicate")
				val o = vf.createLiteral("test value")

				conn.add(s, p, o)
				conn.commit()

				// Query the statement
				val results = conn.getStatements(null, null, null).iterator().asScala.toList
				results should have size 1
				results.head.getSubject shouldBe s
				results.head.getPredicate shouldBe p
				results.head.getObject shouldBe o
			} finally {
				conn.close()
			}
		} finally {
			repo.shutDown()
		}
	}

	it should "persist and reload data" in {
		// Add data
		val sail1 = new SqlSail(tempDir)
		val repo1 = new SailRepository(sail1)
		repo1.init()

		try {
			val conn = repo1.getConnection
			try {
				conn.begin()
				val vf = conn.getValueFactory
				val s = vf.createIRI("http://example.org/subject")
				val p = vf.createIRI("http://example.org/predicate")
				val o = vf.createLiteral("persisted value")

				conn.add(s, p, o)
				conn.commit()
			} finally {
				conn.close()
			}
		} finally {
			repo1.shutDown()
		}

		// Reload and verify
		val sail2 = new SqlSail(tempDir)
		val repo2 = new SailRepository(sail2)
		repo2.init()

		try {
			val conn = repo2.getConnection
			try {
				val results = conn.getStatements(null, null, null).iterator().asScala.toList
				results should have size 1
				results.head.getObject.stringValue() shouldBe "persisted value"
			} finally {
				conn.close()
			}
		} finally {
			repo2.shutDown()
		}
	}

	it should "support named graphs" in {
		val sail = new SqlSail(tempDir)
		val repo = new SailRepository(sail)
		repo.init()

		try {
			val conn = repo.getConnection
			try {
				conn.begin()
				val vf = conn.getValueFactory
				val s1 = vf.createIRI("http://example.org/s1")
				val s2 = vf.createIRI("http://example.org/s2")
				val p = vf.createIRI("http://example.org/p")
				val o1 = vf.createLiteral("value1")
				val o2 = vf.createLiteral("value2")
				val g1 = vf.createIRI("http://example.org/graph1")
				val g2 = vf.createIRI("http://example.org/graph2")

				conn.add(s1, p, o1, g1)
				conn.add(s2, p, o2, g2)
				conn.commit()

				// Query by context
				val results1 = conn.getStatements(null, null, null, g1).iterator().asScala.toList
				results1 should have size 1
				results1.head.getObject shouldBe o1

				val results2 = conn.getStatements(null, null, null, g2).iterator().asScala.toList
				results2 should have size 1
				results2.head.getObject shouldBe o2

				// Query all contexts
				val allResults = conn.getStatements(null, null, null).iterator().asScala.toList
				allResults should have size 2
			} finally {
				conn.close()
			}
		} finally {
			repo.shutDown()
		}
	}

	it should "support transaction rollback" in {
		val sail = new SqlSail(tempDir)
		val repo = new SailRepository(sail)
		repo.init()

		try {
			val conn = repo.getConnection
			try {
				conn.begin()
				val vf = conn.getValueFactory
				val s = vf.createIRI("http://example.org/subject")
				val p = vf.createIRI("http://example.org/predicate")
				val o = vf.createLiteral("should not persist")

				conn.add(s, p, o)
				conn.rollback()

				// Verify nothing was persisted
				val results = conn.getStatements(null, null, null).iterator().asScala.toList
				results should have size 0
			} finally {
				conn.close()
			}
		} finally {
			repo.shutDown()
		}
	}

	it should "remove statements" in {
		val sail = new SqlSail(tempDir)
		val repo = new SailRepository(sail)
		repo.init()

		try {
			val conn = repo.getConnection
			try {
				conn.begin()
				val vf = conn.getValueFactory
				val s = vf.createIRI("http://example.org/subject")
				val p = vf.createIRI("http://example.org/predicate")
				val o = vf.createLiteral("to be removed")

				conn.add(s, p, o)
				conn.commit()

				// Verify added
				var results = conn.getStatements(null, null, null).iterator().asScala.toList
				results should have size 1

				// Remove
				conn.begin()
				conn.remove(s, p, o)
				conn.commit()

				// Verify removed
				results = conn.getStatements(null, null, null).iterator().asScala.toList
				results should have size 0
			} finally {
				conn.close()
			}
		} finally {
			repo.shutDown()
		}
	}

	it should "clear context" in {
		val sail = new SqlSail(tempDir)
		val repo = new SailRepository(sail)
		repo.init()

		try {
			val conn = repo.getConnection
			try {
				conn.begin()
				val vf = conn.getValueFactory
				val s1 = vf.createIRI("http://example.org/s1")
				val s2 = vf.createIRI("http://example.org/s2")
				val p = vf.createIRI("http://example.org/p")
				val o1 = vf.createLiteral("value1")
				val o2 = vf.createLiteral("value2")
				val g1 = vf.createIRI("http://example.org/graph1")
				val g2 = vf.createIRI("http://example.org/graph2")

				conn.add(s1, p, o1, g1)
				conn.add(s2, p, o2, g2)
				conn.commit()

				// Clear graph1
				conn.begin()
				conn.clear(g1)
				conn.commit()

				// Verify graph1 is empty but graph2 still has data
				val results1 = conn.getStatements(null, null, null, g1).iterator().asScala.toList
				results1 should have size 0

				val results2 = conn.getStatements(null, null, null, g2).iterator().asScala.toList
				results2 should have size 1
			} finally {
				conn.close()
			}
		} finally {
			repo.shutDown()
		}
	}

	it should "support namespaces" in {
		val sail = new SqlSail(tempDir)
		val repo = new SailRepository(sail)
		repo.init()

		try {
			val conn = repo.getConnection
			try {
				conn.begin()
				conn.setNamespace("ex", "http://example.org/")
				conn.setNamespace("foaf", "http://xmlns.com/foaf/0.1/")
				conn.commit()

				val ns = conn.getNamespace("ex")
				ns shouldBe "http://example.org/"

				val namespaces = conn.getNamespaces.iterator().asScala.toList
				namespaces should have size 2
			} finally {
				conn.close()
			}
		} finally {
			repo.shutDown()
		}
	}

	it should "query with pattern matching" in {
		val sail = new SqlSail(tempDir)
		val repo = new SailRepository(sail)
		repo.init()

		try {
			val conn = repo.getConnection
			try {
				conn.begin()
				val vf = conn.getValueFactory
				val s1 = vf.createIRI("http://example.org/s1")
				val s2 = vf.createIRI("http://example.org/s2")
				val p1 = vf.createIRI("http://example.org/p1")
				val p2 = vf.createIRI("http://example.org/p2")
				val o = vf.createLiteral("value")

				conn.add(s1, p1, o)
				conn.add(s1, p2, o)
				conn.add(s2, p1, o)
				conn.commit()

				// Query by subject
				val results1 = conn.getStatements(s1, null, null).iterator().asScala.toList
				results1 should have size 2

				// Query by predicate
				val results2 = conn.getStatements(null, p1, null).iterator().asScala.toList
				results2 should have size 2

				// Query by subject and predicate
				val results3 = conn.getStatements(s1, p1, null).iterator().asScala.toList
				results3 should have size 1
			} finally {
				conn.close()
			}
		} finally {
			repo.shutDown()
		}
	}
}
