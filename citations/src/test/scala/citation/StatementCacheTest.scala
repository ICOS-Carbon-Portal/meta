package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.event.NoLogging
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec

class StatementCacheTest extends AnyFunSpec:

	private val factory = org.eclipse.rdf4j.model.impl.SimpleValueFactory.getInstance()

	private def iri(s: String): IRI = factory.createIRI("http://test.icos-cp.eu/" + s)

	private val (g1, g2) = (iri("graph1"), iri("graph2"))
	private val (objA, objB, refB) = (iri("objA"), iri("objB"), iri("refB"))
	private val (name, links, label, isNextVersionOf) = (iri("hasName"), iri("links"), iri("label"), iri("isNextVersionOf"))

	private def makeRepo: Repository =
		val repo = new SailRepository(new MemoryStore)
		val conn = repo.getConnection()
		try
			conn.add(objA, name, factory.createLiteral("a.csv"), g1)
			conn.add(objA, links, refB, g1)
			// same triple in both graphs, to test deduplication of union reads
			conn.add(objA, label, factory.createLiteral("A"), g1)
			conn.add(objA, label, factory.createLiteral("A"), g2)
			conn.add(refB, label, factory.createLiteral("B"), g2)
			conn.add(objB, isNextVersionOf, objA, g2)
		finally conn.close()
		repo

	private def makeConn: (StatementCache, CachingConnection) =
		val cache = new StatementCache(makeRepo, NoLogging)
		(cache, new CachingConnection(cache))

	describe("CachingConnection"){

		it("serves subject patterns across all graphs, deduplicated"){
			val (_, conn) = makeConn
			val all = conn.getStatements(objA, null, null).toIndexedSeq
			assert(all.size === 3)
			assert(conn.getStatements(objA, name, null).toIndexedSeq.map(_.getObject.stringValue) === Seq("a.csv"))
		}

		it("honours named-graph scoping via withContexts"){
			val (_, conn) = makeConn
			val g2view = conn.withContexts(g2, Seq(g2))
			val inG2 = g2view.getStatements(objA, null, null).toIndexedSeq
			assert(inG2.size === 1)
			assert(inG2.head.getPredicate === label)
			assert(g2view.getStatements(refB, null, null).toIndexedSeq.size === 1)
		}

		it("serves reverse lookups"){
			val (_, conn) = makeConn
			val deprecators = conn.getStatements(null, isNextVersionOf, objA).toIndexedSeq
			assert(deprecators.map(_.getSubject) === Seq(objB))
		}

		it("answers hasStatement from the cache"){
			val (_, conn) = makeConn
			assert(conn.hasStatement(objA, name, null))
			assert(!conn.hasStatement(objA, isNextVersionOf, null))
			assert(!conn.hasStatement(iri("absent"), null, null))
		}

		it("does not re-query for subjects known to be absent"){
			val (cache, conn) = makeConn
			assert(conn.getStatements(iri("absent"), null, null).toIndexedSeq.isEmpty)
			val queriesAfterFirst = cache.stats.queries
			assert(conn.getStatements(iri("absent"), null, null).toIndexedSeq.isEmpty)
			assert(cache.stats.queries === queriesAfterFirst)
		}

		it("stays consistent under concurrent access"){
			import scala.concurrent.{Await, Future}
			import scala.concurrent.duration.DurationInt
			import scala.concurrent.ExecutionContext.Implicits.global
			val (cache, conn) = makeConn
			val readers = (1 to 16).map: _ =>
				Future:
					(1 to 200).foreach: _ =>
						assert(conn.getStatements(objA, null, null).toIndexedSeq.size === 3)
						assert(conn.getStatements(null, isNextVersionOf, objA).toIndexedSeq.map(_.getSubject) === Seq(objB))
						assert(conn.hasStatement(objA, name, null))
						cache.prefetch(Seq(objA, objB))
			Await.result(Future.sequence(readers), 30.seconds)
		}

		it("serves prefetched subjects, their referrers and referenced resources without further queries"){
			val (cache, conn) = makeConn
			cache.prefetch(Seq(objA))
			val queriesAfterPrefetch = cache.stats.queries
			assert(conn.getStatements(objA, null, null).toIndexedSeq.size === 3)
			// one hop out from objA
			assert(conn.getStatements(refB, null, null).toIndexedSeq.size === 1)
			// incoming statements of objA, and the referrer objB itself
			assert(conn.getStatements(null, isNextVersionOf, objA).toIndexedSeq.size === 1)
			assert(conn.getStatements(objB, null, null).toIndexedSeq.size === 1)
			assert(cache.stats.queries === queriesAfterPrefetch)
		}
	}

end StatementCacheTest
