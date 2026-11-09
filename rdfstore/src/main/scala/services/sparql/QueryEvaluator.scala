package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.{ByteString, ByteStringBuilder}
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory
import org.eclipse.rdf4j.query.{GraphQuery, Query, QueryInterruptedException, TupleQuery}
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.rio.RDFWriterFactory

import java.io.OutputStream

/**
 * Evaluates SPARQL queries as Akka Streams sources of serialized query results.
 *
 * Closing a query result from another thread while it is being iterated frees the native LMDB
 * cursor under the reader and crashes the JVM (SIGSEGV in liblwjgl_lmdb.so). Here, all access to
 * the query result, including closing it, happens in a single `unfoldResource` stage, which Akka never
 * calls concurrently. Cancellation of the stream (timeout, client disconnect, failure) is only acted upon
 * between reads, after which the stage closes the result itself.
 */
trait QueryEvaluator[Q <: Query] {
	import QueryEvaluator.*

	/** Evaluates the query and starts writing the results (lazily) to `os` */
	protected def startWriting(query: Q, os: OutputStream): ResultWriting

	/**
	 * A source that (upon materialization) opens a connection, evaluates the query and streams the serialized results.
	 * The source performs blocking I/O, so it should be run on a dedicated dispatcher.
	 * `cancellation` is checked by the triple store during the reads, see QueryCancellation.
	 * `onFinish` is called once the query result and the connection have been closed (or failed to open).
	 */
	def source(
		getConnection: () => RepositoryConnection,
		queryStr: String,
		cancellation: QueryCancellation,
		onFinish: () => Unit
	): Source[ByteString, NotUsed] = Source.unfoldResource[ByteString, QuerySession](
		create = () => {
			try {
				QueryCancellation.within(cancellation) {
					val conn = getConnection()
					try {
						val buf = ByteStringBuilder()
						val query = conn.prepareQuery(queryStr).asInstanceOf[Q]
						QuerySession(conn, buf, startWriting(query, buf.asOutputStream), onFinish)
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
}

object QueryEvaluator {
	private val ChunkSize = 1 << 16

	trait ResultWriting {
		/** Writes the next query result, or finishes the document if there are none; returns false in the latter case */
		def writeNext(): Boolean
		def close(): Unit
	}

	private class QuerySession(conn: RepositoryConnection, buf: ByteStringBuilder, writing: ResultWriting, onFinish: () => Unit) {
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
}


/**
 * A single `hasNext` of a query result can run for minutes (e.g. ORDER BY materializing all solutions),
 * during which the stream stage cannot react to cancellation. So, while a thread reads the query result,
 * the cancellation flag is published to it as `current`, and the triple source checks it on every statement step.
 */
object QueryCancellation {
	private val current = ThreadLocal[QueryCancellation]()

	/** Throws QueryInterruptedException if the query evaluated by the current thread was cancelled */
	def check(): Unit = {
		val c = current.get()
		if (c != null) c.check()
	}

	private[sparql] def within[T](c: QueryCancellation)(thunk: => T): T = {
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

class QueryCancellation {
	@volatile private var cancelled = false

	/** Makes the query fail on the thread evaluating it; returns immediately */
	def cancel(): Unit = cancelled = true

	def check(): Unit = {
		if (cancelled) throw QueryInterruptedException("SPARQL query evaluation was cancelled")
	}
}


class TupleQueryEvaluator(wf: TupleQueryResultWriterFactory) extends QueryEvaluator[TupleQuery] {

	override protected def startWriting(query: TupleQuery, os: OutputStream): QueryEvaluator.ResultWriting = {
		val writer = wf.getWriter(os)
		val res = query.evaluate()
		try {
			writer.startQueryResult(res.getBindingNames())
		} catch {
			case err: Throwable =>
				res.close()
				throw err
		}

		new QueryEvaluator.ResultWriting {
			def writeNext(): Boolean = {
				if (res.hasNext()) {
					writer.handleSolution(res.next())
					true
				} else {
					writer.endQueryResult()
					false
				}
			}

			def close(): Unit = res.close()
		}
	}
}


class GraphQueryEvaluator(wf: RDFWriterFactory) extends QueryEvaluator[GraphQuery] {

	override protected def startWriting(query: GraphQuery, os: OutputStream): QueryEvaluator.ResultWriting = {
		val writer = wf.getWriter(os)
		val res = query.evaluate()
		try {
			writer.startRDF()
			res.getNamespaces().forEach(writer.handleNamespace)
		} catch {
			case err: Throwable =>
				res.close()
				throw err
		}

		new QueryEvaluator.ResultWriting {
			def writeNext(): Boolean = {
				if (res.hasNext()) {
					writer.handleStatement(res.next())
					true
				} else {
					writer.endRDF()
					false
				}
			}

			def close(): Unit = res.close()
		}
	}
}
