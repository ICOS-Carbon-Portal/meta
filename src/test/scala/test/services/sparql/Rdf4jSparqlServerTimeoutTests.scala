package se.lu.nateko.cp.meta.test.services.sparql

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshalling
import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.Sink
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import org.eclipse.rdf4j.query.{BindingSet, TupleQuery, TupleQueryResult}
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.services.sparql.magic.{CpEnrichedTripleSource, StatementsEnricher}

import java.lang.reflect.Proxy
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
 * Reproduces the 2026-11-04 cpmeta SIGSEGV: when a query exceeds maxQueryRuntimeSec,
 * Rdf4jSparqlServer closes the query result from the scheduler thread while the worker
 * thread is still iterating it. With LMDB this frees the cursor under the reader.
 */
class Rdf4jSparqlServerTimeoutTests extends AnyFunSpec with BeforeAndAfterAll {

	private given system: ActorSystem = ActorSystem("sparql-timeout-tests")

	override def afterAll(): Unit = Await.ready(system.terminate(), 10.seconds)

	private val config = SparqlServerConfig(
		maxQueryRuntimeSec = 1,
		quotaPerMinute = 1000,
		quotaPerHour = 10000,
		maxParallelQueries = 5,
		maxQueryQueue = 10,
		banLength = 1,
		maxCacheableQuerySize = 10000,
		adminUsers = Nil
	)

	describe("Rdf4jSparqlServer query timeout") {

		it("does not close the query result while a worker thread is still reading from it") {
			val result = FakeLmdbResult(() => Thread.sleep(3000))
			val server = Rdf4jSparqlServer(mockRepo(FakeConnection(result)), config)

			try {
				val streamDone = runTimingOutQuery(server)

				assert(result.readerFinished.await(10, TimeUnit.SECONDS), "worker never finished the simulated cursor scan")
				assert(streamDone.value.exists(_.isFailure), "expected the timeout to terminate the response stream")
				assert(result.unsafeClose.get.isEmpty, result.unsafeClose.get.getOrElse(""))
			} finally {
				server.shutdown()
			}
		}

		/*
		 * An ORDER BY query materializes all solutions inside a single hasNext() call of the top-level
		 * result (OrderIterator), scanning the triple store all the while. If cancellation is only checked
		 * between results, the worker keeps reading LMDB long after the timeout, and crashes the JVM when
		 * the store is shut down underneath it (seen in the test suite as a SIGSEGV on exit).
		 */
		it("stops a cancelled query in the middle of a long triple store scan") {
			val tripleSrc = CpEnrichedTripleSource(SlowTripleSource(nStatements = 10000), StatementsEnricher(null))
			val result = FakeLmdbResult(() => {
				val statements = tripleSrc.getStatements(null, null, null)
				try {
					while (statements.hasNext()) statements.next()
				} finally {
					statements.close()
				}
			})
			val server = Rdf4jSparqlServer(mockRepo(FakeConnection(result)), config)

			try {
				val streamDone = runTimingOutQuery(server)
				assert(streamDone.value.exists(_.isFailure), "expected the timeout to terminate the response stream")

				assert(
					result.readerFinished.await(2, TimeUnit.SECONDS),
					"worker kept scanning the triple store after the query was cancelled"
				)
				assert(result.unsafeClose.get.isEmpty, result.unsafeClose.get.getOrElse(""))
			} finally {
				server.shutdown()
			}
		}

		/*
		 * When a client's queue overflows, QuotaManager bans the client and silently drops the queued
		 * evaluations. Any query result or repository connection opened for them must still get closed
		 * (ideally, queued queries are not evaluated at all until they are let to run).
		 */
		it("closes query results and connections of queued queries dropped by the quota manager") {
			val queueingConfig = config.copy(maxParallelQueries = 1, maxQueryQueue = 1)
			val running = FakeConnection(FakeLmdbResult(() => Thread.sleep(3000)))
			val queued = FakeConnection(FakeLmdbResult(() => ()))
			val overflowing = FakeConnection(FakeLmdbResult(() => ()))
			val conns = Seq(running, queued, overflowing)
			val server = Rdf4jSparqlServer(mockRepo(conns*), queueingConfig)

			try {
				val runningDone = startQuery(server)
				assert(running.evaluated.await(5, TimeUnit.SECONDS), "the running query was never evaluated")
				//both queued at once while the first query occupies the only slot, overflowing the queue
				val streamsDone = runningDone +: Seq.fill(2)(startQuery(server))

				streamsDone.foreach(Await.ready(_, 10.seconds))
				assert(running.result.readerFinished.await(10, TimeUnit.SECONDS), "worker never finished the simulated cursor scan")

				eventually {
					for ((conn, name) <- conns.zip(Seq("running", "queued", "overflowing")) if conn.evaluated.getCount == 0) {
						assert(conn.result.closed.get, s"the $name query's result was never closed")
						assert(conn.closed.get, s"the $name query's connection was never closed")
					}
				}
			} finally {
				server.shutdown()
			}
		}
	}

	private def eventually(assertion: => Unit): Unit = {
		val deadline = 10.seconds.fromNow
		var passed = false
		while (!passed) {
			try {
				assertion
				passed = true
			} catch {
				case _: org.scalatest.exceptions.TestFailedException if deadline.hasTimeLeft() =>
					Thread.sleep(100)
			}
		}
	}

	private def runTimingOutQuery(server: Rdf4jSparqlServer) = Await.ready(startQuery(server), 10.seconds)

	private def startQuery(server: Rdf4jSparqlServer) = {
		val query = SparqlQuery("select * where {?s ?p ?o}", Some("124.243.154.220"))
		val marshallings = Await.result(server.marshaller(query)(system.dispatcher), 5.seconds)
		val response = marshallings.collectFirst {
			case m: Marshalling.WithFixedContentType[HttpResponse @unchecked] => m.marshal()
		}.get

		response.entity.dataBytes.runWith(Sink.ignore)
	}

	/**
	 * Stands in for an LMDB-backed result: `hasNext` runs `scan`, simulating a long cursor scan
	 * (`LmdbRecordIterator.next -> mdb_cursor_get`). Closing it from another thread
	 * during that scan is what segfaults in production; here it is recorded instead.
	 */
	private class FakeLmdbResult(scan: () => Unit) extends TupleQueryResult {
		private val reader = AtomicReference[Thread](null)
		val unsafeClose = AtomicReference[Option[String]](None)
		val readerFinished = CountDownLatch(1)
		val closed = AtomicBoolean(false)

		override def getBindingNames(): java.util.List[String] = java.util.List.of("s", "p", "o")

		override def hasNext(): Boolean = {
			reader.set(Thread.currentThread())
			try {
				scan()
				false
			} finally {
				reader.set(null)
				readerFinished.countDown()
			}
		}

		override def next(): BindingSet = throw java.util.NoSuchElementException()

		override def remove(): Unit = throw UnsupportedOperationException()

		override def close(): Unit = {
			closed.set(true)
			val readerThread = reader.get()
			val closingThread = Thread.currentThread()
			if (readerThread != null && readerThread != closingThread) {
				unsafeClose.compareAndSet(None, Some(
					s"Thread '${closingThread.getName}' closed the result while thread '${readerThread.getName}' " +
					"was still reading from it (use-after-free of the LMDB cursor)"
				))
			}
		}
	}

	/** A triple store whose statement scan takes about a millisecond per statement */
	private class SlowTripleSource(nStatements: Int) extends TripleSource {
		private val factory = SimpleValueFactory.getInstance()
		private val iri = factory.createIRI("http://example.org/s")

		override def getValueFactory(): ValueFactory = factory

		override def getStatements(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): CloseableIteration[? <: Statement] =
			new CloseableIteration[Statement] {
				private var remaining = nStatements
				override def hasNext(): Boolean = remaining > 0
				override def next(): Statement = {
					Thread.sleep(1)
					remaining -= 1
					factory.createStatement(iri, iri, iri)
				}
				override def remove(): Unit = throw UnsupportedOperationException()
				override def close(): Unit = ()
			}
	}

	private class FakeConnection(val result: FakeLmdbResult) {
		val evaluated = CountDownLatch(1)
		val closed = AtomicBoolean(false)

		private val query = mock(classOf[TupleQuery]) {
			case ("evaluate", Array()) =>
				evaluated.countDown()
				result
		}

		val conn = mock(classOf[RepositoryConnection]) {
			case ("prepareQuery", _) => query
			case ("close", _) =>
				closed.set(true)
				null
		}
	}

	/** Hands out the given connections in order, one per query */
	private def mockRepo(conns: FakeConnection*): Repository = {
		val nextConn = AtomicInteger(0)
		mock(classOf[Repository]) {
			case ("getConnection", _) => conns(nextConn.getAndIncrement()).conn
		}
	}

	private def mock[T](cls: Class[T])(handler: PartialFunction[(String, Array[AnyRef]), AnyRef]): T =
		Proxy.newProxyInstance(cls.getClassLoader, Array(cls), (proxy, method, args) => {
			val call = method.getName -> Option(args).getOrElse(Array.empty[AnyRef])
			handler.applyOrElse(call, {
				case ("toString", _) => s"mock ${cls.getSimpleName}"
				case ("hashCode", _) => Integer.valueOf(System.identityHashCode(proxy))
				case ("equals", Array(other)) => java.lang.Boolean.valueOf(proxy eq other)
				case _ if method.getReturnType == Void.TYPE => null
				case (name, _) => throw UnsupportedOperationException(s"${cls.getSimpleName}.$name is not mocked")
			})
		}).asInstanceOf[T]
}
