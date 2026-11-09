package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.{ByteString, ByteStringBuilder}
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.query.{Query, QueryInterruptedException, QueryResult}
import org.eclipse.rdf4j.repository.RepositoryConnection

import scala.concurrent.ExecutionContext

/**
 * Streams SPARQL query results as Akka Streams sources of serialized query results.
 *
 * Closing a query result from another thread while it is being iterated frees the native LMDB
 * cursor under the reader and crashes the JVM (SIGSEGV in liblwjgl_lmdb.so). Here, all access to
 * the query result, including closing it, happens in a single `unfoldResource` stage, which Akka never
 * calls concurrently. The stage only acts on cancellation of the stream (timeout, client disconnect, failure)
 * between reads, after which it closes the result itself. As a single read can take minutes, the cancellation
 * is also signalled to the triple store, see QueryCancellation.
 */
private[sparql] object QueryStreaming {
	/**
	 * Approximate size (64 KiB) of the serialized results emitted per stream element. Each `read` keeps writing
	 * results until the buffer reaches this size, so a chunk can overshoot by up to one result. Bigger chunks
	 * mean less per-element stream overhead, but more memory per query.
	 */
	private val ChunkSize = 1 << 16

	/**
	 * A source that (upon materialization) opens a connection, evaluates the query and streams the serialized results.
	 * The blocking I/O runs in its own async island on `dispatcher`.
	 * `onFinish` is called once the query result and the connection have been closed (or failed to open).
	 */
	def source[Q <: Query](
		evaluator: QueryEvaluator[Q],
		getConnection: () => RepositoryConnection,
		queryStr: String,
		dispatcher: String,
		onFinish: () => Unit
	): Source[ByteString, NotUsed] = Source.fromMaterializer { (_, _) =>
		val cancellation = QueryCancellation()
		Source.unfoldResource[ByteString, QuerySession](
			create = () => {
				try {
					QueryCancellation.within(cancellation) {
						val conn = getConnection()
						try {
							val buf = ByteStringBuilder()
							val query = conn.prepareQuery(queryStr).asInstanceOf[Q]
							QuerySession(conn, buf, evaluator.evaluate(query, buf.asOutputStream), onFinish)
						} catch {
							case err: Throwable =>
								conn.close()
								throw err
						}
					}
				} catch {
					case err: Throwable =>
						onFinish()
						throw err
				}
			},
			read = sess => QueryCancellation.within(cancellation)(sess.readChunk()),
			close = _.close()
		)
		.async(dispatcher)
		// outside of the async island, so it sees the stream end even while the island is stuck in a long read
		.watchTermination() { (_, done) =>
			done.onComplete(_ => cancellation.cancel())(using ExecutionContext.parasitic)
			NotUsed
		}
	}.mapMaterializedValue(_ => NotUsed)

	/**
	 * Lets a cancelled query stop in the middle of a long triple store scan.
	 * To be applied to the statement iterations the triple source of the query evaluation returns.
	 */
	def cancellable[T](iteration: => CloseableIteration[? <: T]): CloseableIteration[T] = {
		QueryCancellation.check()
		CancellingIteration(iteration)
	}

	/** Writes the query results one by one; `start` is run immediately, closing the result if it fails */
	class ResultWriting[T](res: QueryResult[T], start: => Unit, handle: T => Unit, end: () => Unit) {
		try {
			start
		} catch {
			case err: Throwable =>
				res.close()
				throw err
		}

		/** Writes the next query result, or finishes the document if there are none; returns false in the latter case */
		def writeNext(): Boolean = {
			if (res.hasNext()) {
				handle(res.next())
				true
			} else {
				end()
				false
			}
		}

		def close(): Unit = res.close()
	}

	private class QuerySession(conn: RepositoryConnection, buf: ByteStringBuilder, writing: ResultWriting[?], onFinish: () => Unit) {
		private var finished = false

		def readChunk(): Option[ByteString] = {
			if (finished) None
			else {
				while (!finished && buf.length < ChunkSize) {
					finished = !writing.writeNext()
				}
				val chunk = buf.result()
				buf.clear()
				Some(chunk)
			}
		}

		def close(): Unit = {
			try writing.close()
			finally {
				try conn.close()
				finally onFinish()
			}
		}
	}

	private class CancellingIteration[T](inner: CloseableIteration[? <: T]) extends CloseableIteration[T] {
		override def hasNext(): Boolean = {
			QueryCancellation.check()
			inner.hasNext()
		}
		override def next(): T = inner.next()
		override def remove(): Unit = inner.remove()
		override def close(): Unit = inner.close()
	}

	/**
	 * A single `hasNext` of a query result can run for minutes (e.g. ORDER BY materializing all solutions),
	 * during which the stream stage cannot react to cancellation. So, while a thread reads the query result,
	 * the cancellation flag is published to it as `current`, and the triple source checks it on every statement step.
	 */
	private object QueryCancellation {
		private val current = ThreadLocal[QueryCancellation]()

		/** Throws QueryInterruptedException if the query evaluated by the current thread was cancelled */
		def check(): Unit = {
			val c = current.get()
			if (c != null) c.check()
		}

		def within[T](c: QueryCancellation)(thunk: => T): T = {
			val prev = current.get()
			current.set(c)
			try {
				c.check()
				thunk
			} finally {
				current.set(prev)
			}
		}
	}

	private class QueryCancellation {
		@volatile private var cancelled = false

		/** Makes the query fail on the thread evaluating it; returns immediately */
		def cancel(): Unit = cancelled = true

		def check(): Unit = {
			if (cancelled) throw QueryInterruptedException("SPARQL query evaluation was cancelled")
		}
	}
}
