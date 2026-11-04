package se.lu.nateko.cp.meta.test.services.sparql

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshalling
import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.Sink
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.TupleQueryResult
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import org.lwjgl.system.libc.{LibCStdlib, LibCString}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.SparqlQuery
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriterFactory
import se.lu.nateko.cp.meta.services.sparql.{Rdf4jSparqlServer, TupleQueryEvaluator}

import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt
import scala.jdk.FutureConverters.*
import scala.util.{Try, Using}

/**
 * Closing a query result from another thread while it is being iterated used to free the LMDB cursor
 * under the reader, crashing the JVM with SIGSEGV in liblwjgl_lmdb.so (RDF4J GH-5374, fixed in 5.1.4).
 * Rdf4jSparqlServer does exactly that when a query exceeds maxQueryRuntimeSec.
 * If these tests regress, the symptom is the test JVM dying rather than a test failure.
 */
class LmdbQueryTimeoutTests extends AnyFunSpec with BeforeAndAfterAll {

	private given system: ActorSystem = ActorSystem("lmdb-query-timeout-tests")

	private val storeDir = Files.createTempDirectory("lmdb-query-timeout-tests")
	private val lmdb = LmdbStore(storeDir.toFile, LmdbStoreConfig())
	private val repo = SailRepository(lmdb)

	private val nStatements = 20000
	private val iterations = 200

	/** Matches nothing, so the first hasNext() of the result keeps reading the LMDB cursor through the whole store */
	private val scanQuery = "select * where {?s ?p ?o filter(?o < 0)}"

	/**
	 * Waits until `reader` is executing LMDB code in the middle of the scan. Closing the result at that point
	 * makes the reader use the freed cursor when it next reads from it, unless RDF4J guards against that.
	 * Merely waiting for the scan to start would make the tests depend on thread scheduling: with few CPUs,
	 * the reader could finish before the close, and the bug would go unnoticed.
	 */
	private def awaitScanning(reader: Thread, scan: java.util.concurrent.Future[?]): Unit = {
		val deadline = System.nanoTime() + 10.seconds.toNanos
		while (!reader.getStackTrace.exists(_.getClassName.startsWith("org.eclipse.rdf4j.sail.lmdb."))) {
			assert(!scan.isDone, "the scan finished before it could be interrupted; increase nStatements")
			assert(System.nanoTime() < deadline, "the reader never started scanning the store")
			Thread.onSpinWait()
		}
	}

	private val scribbles = java.util.concurrent.ConcurrentLinkedQueue[java.lang.Long]()

	/**
	 * Overwrites memory just freed by the current thread with 0xFF. glibc hands the most recently freed chunk of
	 * each size class back to the next malloc of that size on the same thread, so allocating one chunk per class,
	 * up to the largest one glibc caches per thread, covers the freed LMDB cursor (a few hundred bytes).
	 */
	private def scribbleOverFreedMemory(): Unit =
		for (size <- 16 to 1024 by 16) {
			val ptr = LibCStdlib.nmalloc(size.toLong)
			LibCString.nmemset(ptr, 0xFF, size.toLong)
			scribbles.add(ptr)
		}

	private def freeScribbles(): Unit =
		while (!scribbles.isEmpty) LibCStdlib.nfree(scribbles.poll())

	/**
	 * Takes the store-wide LMDB lock that every cursor read (and cross-thread cursor close) has to wait for.
	 * Relies on LmdbStore internals: `lock` (StampedLock) before RDF4J 5.1, `lockManager` from 5.1.
	 * Returns a function that releases the lock.
	 */
	private def lockStore(): () => Unit = {
		def field(obj: AnyRef, name: String): AnyRef = {
			val f = obj.getClass.getDeclaredField(name)
			f.setAccessible(true)
			f.get(obj)
		}
		val txnManager = field(field(field(lmdb, "backingStore"), "tripleStore"), "txnManager")
		val lock = Try(field(txnManager, "lockManager")).getOrElse(field(txnManager, "lock"))
		val stamp = lock.getClass.getMethod("writeLock").invoke(lock)
		() => lock.getClass.getMethod("unlockWrite", classOf[Long]).invoke(lock, stamp)
	}

	/** Waits until `thread` is inside LmdbRecordIterator.`method`, blocked on the store lock (or until `task` is done) */
	private def awaitWaitingForLock(thread: Thread, task: java.util.concurrent.Future[?], method: String, orDone: Boolean = false): Unit = {
		def isWaiting = {
			val stack = thread.getStackTrace
			val lmdbFrame = stack.indexWhere(f => f.getClassName.endsWith(".LmdbRecordIterator") && f.getMethodName == method)
			lmdbFrame > 0 && stack.take(lmdbFrame).exists(_.getClassName.contains("Lock"))
		}
		val deadline = System.nanoTime() + 10.seconds.toNanos
		while (!isWaiting) {
			if (task.isDone) {
				assert(orDone, s"expected LmdbRecordIterator.$method to wait for the store lock")
				return
			}
			assert(System.nanoTime() < deadline, s"timed out waiting for LmdbRecordIterator.$method to block")
			Thread.onSpinWait()
		}
	}

	override def beforeAll(): Unit = {
		val f = SimpleValueFactory.getInstance()
		val pred = f.createIRI("http://example.org/p")
		Using.resource(repo.getConnection()) { conn =>
			conn.begin()
			for (i <- 1 to nStatements) conn.add(f.createIRI(s"http://example.org/s$i"), pred, f.createLiteral(i))
			conn.commit()
		}
	}

	override def afterAll(): Unit = {
		repo.shutDown()
		Await.ready(system.terminate(), 10.seconds)
		Files.walk(storeDir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
	}

	describe("LMDB query results") {

		/*
		 * Deterministic reproduction of RDF4J GH-5374. The reader is parked in LmdbRecordIterator.next(), past every
		 * isClosed() check, waiting for the store lock that the test holds. Meanwhile the thread that opened the cursor
		 * closes the result, which frees the cursor without taking the lock. Before RDF4J 5.1.4, the reader then went
		 * on to use the freed cursor once the lock was released. Freed memory usually still looks like a valid cursor,
		 * so the closing thread overwrites it, which makes the use-after-free crash the JVM every time.
		 */
		it("can be closed while another thread is waiting to read from it") {
			val creator = Executors.newSingleThreadExecutor()
			val reader = Executors.newSingleThreadExecutor()
			try {
				val creatorThread = creator.submit[Thread](() => Thread.currentThread()).get()
				val readerThread = reader.submit[Thread](() => Thread.currentThread()).get()
				Using.resource(repo.getConnection()) { conn =>
					val res = creator.submit[TupleQueryResult](() => {
						val query = conn.prepareTupleQuery("select * where {?s ?p ?o}")
						query.setIncludeInferred(false)
						val res = query.evaluate()
						res.hasNext() // makes sure the LMDB cursor is opened on this thread
						res
					}).get()

					val unlockStore = lockStore()
					val scanned =
						try {
							// the result of the last hasNext() call, the one waiting for the lock while the result is closed
							val scanned = reader.submit[Option[Boolean]](() => {
								var lastHasNext: Option[Boolean] = None
								try {
									while ({ lastHasNext = Some(res.hasNext()); lastHasNext.get }) {
										res.next()
										lastHasNext = None
									}
								} catch case _: Exception => () // the result may report it was closed
								lastHasNext
							})
							awaitWaitingForLock(readerThread, scanned, "next")
							val closed = creator.submit[Unit](() => {
								res.close()
								scribbleOverFreedMemory()
							})
							// closing on the cursor's own thread does not wait for the lock, but other cursors' closing might
							awaitWaitingForLock(creatorThread, closed, "closeInternal", orDone = true)
							scanned
						} finally unlockStore()

					freeScribbles()
					assert(
						scanned.get(10, TimeUnit.SECONDS) != Some(true),
						"read a statement from an LMDB cursor freed by closing the result (use-after-free, RDF4J GH-5374)"
					)
				}
			} finally {
				reader.shutdown()
				creator.shutdown()
			}
		}

		/*
		 * LmdbRecordIterator skips locking when closed by the thread that created it, so a close from that thread
		 * races with the reader inside native LMDB code. This cannot be made deterministic, so it is a stress test.
		 */
		it("can be closed by the thread that started the evaluation") {
			val reader = Executors.newSingleThreadExecutor()
			given ExecutionContext = ExecutionContext.fromExecutor(reader)
			val evaluator = TupleQueryEvaluator(SPARQLResultsJSONWriterFactory())
			try {
				val readerThread = reader.submit[Thread](() => Thread.currentThread()).get()
				for (_ <- 1 to iterations) {
					val conn = repo.getConnection()
					val query = conn.prepareTupleQuery(scanQuery)
					val (closer, done) = evaluator.evaluate(query, OutputStream.nullOutputStream()).get
					awaitScanning(readerThread, done.asJava.toCompletableFuture)
					Try(closer.close())
					Await.ready(done, 10.seconds)
					conn.close()
				}
			} finally {
				reader.shutdown()
			}
		}
	}

	describe("Rdf4jSparqlServer query timeout") {

		it("terminates a long-running query on an LMDB store") {
			val config = SparqlServerConfig(
				maxQueryRuntimeSec = 1,
				quotaPerMinute = 1000,
				quotaPerHour = 10000,
				maxParallelQueries = 5,
				maxQueryQueue = 10,
				banLength = 1,
				maxCacheableQuerySize = 10000,
				adminUsers = Nil
			)
			val server = Rdf4jSparqlServer(repo, config)
			// nStatements^3 joined solutions, materialized by ORDER BY within a single hasNext() call
			val query = SparqlQuery(
				"select * where {?s1 ?p ?o1 . ?s2 ?p ?o2 . ?s3 ?p ?o3} order by ?o1 ?o2 ?o3",
				Some("124.243.154.220")
			)
			try {
				val marshallings = Await.result(server.marshaller(query)(system.dispatcher), 5.seconds)
				val response = marshallings.collectFirst {
					case m: Marshalling.WithFixedContentType[HttpResponse @unchecked] => m.marshal()
				}.get
				val streamDone = Await.ready(response.entity.dataBytes.runWith(Sink.ignore), 10.seconds)
				assert(streamDone.value.exists(_.isFailure), "expected the timeout to terminate the response stream")
			} finally {
				server.shutdown()
			}
		}
	}
}
