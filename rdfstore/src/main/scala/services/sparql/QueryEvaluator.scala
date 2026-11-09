package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import akka.Done
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.query.impl.{IteratingGraphQueryResult, IteratingTupleQueryResult}
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory
import org.eclipse.rdf4j.query.{GraphQuery, Query, QueryInterruptedException, QueryResults, TupleQuery}
import org.eclipse.rdf4j.rio.RDFWriterFactory

import java.io.OutputStream
import java.lang.AutoCloseable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * @param start evaluates the query and returns the reporting of its results to the output stream
 */
class QueryEvaluator[Q <: Query](start: (Q, OutputStream) => () => Unit) {
	/**
	 * Starts evaluating the query, writing the results to `os` asynchronously.
	 * The query result is always closed by the thread that iterates it, never by the caller.
	 */
	def evaluate(query: Q, os: OutputStream)(using ExecutionContext): Try[(AutoCloseable, Future[Done])] =
		Try:
			val report = start(query, os)
			val cancellation = QueryCancellation()
			cancellation -> Future:
				QueryCancellation.within(cancellation)(report())
				Done
}

private[sparql] object QueryEvaluator {

	def apply(wf: TupleQueryResultWriterFactory): QueryEvaluator[TupleQuery] =
		evaluator(wf.getWriter(_), (_: TupleQuery).evaluate()): (res, writer) =>
			QueryResults.report(IteratingTupleQueryResult(res.getBindingNames(), CancellingIteration(res)), writer)

	def apply(wf: RDFWriterFactory): QueryEvaluator[GraphQuery] =
		evaluator(wf.getWriter(_), (_: GraphQuery).evaluate()): (res, writer) =>
			QueryResults.report(IteratingGraphQueryResult(res.getNamespaces(), CancellingIteration(res)), writer)

	private def evaluator[Q <: Query, R, W](getWriter: OutputStream => W, evaluate: Q => R)(report: (R, W) => Unit) =
		new QueryEvaluator[Q]((query, os) =>
			val writer = getWriter(os)
			val res = evaluate(query)
			() => report(res, writer)
		)
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
class QueryCancellation extends AutoCloseable {
	@volatile private var cancelled = false

	/** Asks the evaluating thread to stop and close the query result; returns immediately. */
	override def close(): Unit = cancelled = true

	private def check(): Unit = {
		if (cancelled) throw QueryInterruptedException("SPARQL query evaluation was cancelled")
	}
}

private[sparql] object QueryCancellation {
	private val current = ThreadLocal[QueryCancellation]()

	/** Throws QueryInterruptedException if the query evaluated by the current thread was cancelled */
	def check(): Unit = {
		val c = current.get()
		if (c != null) c.check()
	}

	def within[T](c: QueryCancellation)(thunk: => T): T = {
		val prev = current.get()
		current.set(c)
		try thunk finally current.set(prev)
	}
}

/** Lets a cancelled SPARQL query stop between iteration steps, see QueryCancellation */
private[sparql] class CancellingIteration[T](inner: CloseableIteration[? <: T]) extends CloseableIteration[T] {

	override def hasNext(): Boolean =
		QueryCancellation.check()
		inner.hasNext()

	override def next(): T = inner.next()
	override def remove(): Unit = inner.remove()
	override def close(): Unit = inner.close()
}
