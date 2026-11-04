package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import akka.Done
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.query.{BindingSet, GraphQuery, GraphQueryResult, Query, QueryInterruptedException, QueryResult, QueryResults, TupleQuery, TupleQueryResult}
import org.eclipse.rdf4j.rio.RDFWriterFactory

import java.io.OutputStream
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait QueryEvaluator[Q <: Query] {
	/**
	 * Starts evaluating the query, writing the results to `os` asynchronously.
	 * The query result is always closed by the thread that iterates it, never by the caller.
	 */
	def evaluate(query: Q, os: OutputStream)(using ExecutionContext): Try[(QueryCloser, Future[Done])]
}

trait QueryCloser {
	/** Asks the evaluating thread to stop and close the query result; returns immediately. */
	def signalClose(): Unit
}

object QueryCloser {
	val NoOp: QueryCloser = () => ()
}

/**
 * Closing a query result from another thread while it is being iterated frees the native LMDB
 * cursor under the reader and crashes the JVM (SIGSEGV in liblwjgl_lmdb.so). Hence cancellation
 * is cooperative: the cancelled query fails on the iterating thread, which then closes the result.
 *
 * Checking the flag only in the top-level result is not enough, as a single `hasNext` can run for
 * minutes (e.g. ORDER BY materializing all solutions). So while the iterating thread drives the query,
 * the flag is published to it as `current`, and the triple source checks it on every statement step.
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

private[sparql] class QueryCancellation extends QueryCloser {
	@volatile private var cancelled = false

	def signalClose(): Unit = cancelled = true

	def check(): Unit = {
		if (cancelled) throw QueryInterruptedException("SPARQL query evaluation was cancelled")
	}
}

private trait CancellableResult[T] extends QueryResult[T] with QueryCloser {
	protected def inner: QueryResult[T]
	private val cancellation = QueryCancellation()

	def signalClose(): Unit = cancellation.signalClose()

	override def hasNext(): Boolean = QueryCancellation.within(cancellation)(inner.hasNext())
	override def next(): T = QueryCancellation.within(cancellation)(inner.next())
	override def remove(): Unit = inner.remove()
	override def close(): Unit = inner.close()
}

private class CancellableTupleResult(protected val inner: TupleQueryResult)
	extends TupleQueryResult with CancellableResult[BindingSet] {
	override def getBindingNames(): java.util.List[String] = inner.getBindingNames()
}

private class CancellableGraphResult(protected val inner: GraphQueryResult)
	extends GraphQueryResult with CancellableResult[Statement] {
	override def getNamespaces(): java.util.Map[String, String] = inner.getNamespaces()
}


class TupleQueryEvaluator(wf: TupleQueryResultWriterFactory) extends QueryEvaluator[TupleQuery]:

	override def evaluate(query: TupleQuery, os: OutputStream)(using ExecutionContext): Try[(QueryCloser, Future[Done])] =
		Try:
			val resultWriter = wf.getWriter(os)
			val tupleRes = CancellableTupleResult(query.evaluate())
			tupleRes -> Future:
				QueryResults.report(tupleRes, resultWriter)
				Done


class GraphQueryEvaluator(wf: RDFWriterFactory) extends QueryEvaluator[GraphQuery]:

	override def evaluate(query: GraphQuery, os: OutputStream)(using ExecutionContext): Try[(QueryCloser, Future[Done])] =
		Try:
			val resultWriter = wf.getWriter(os)
			val graphRes = CancellableGraphResult(query.evaluate())
			graphRes -> Future:
				QueryResults.report(graphRes, resultWriter)
				Done
