package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import akka.Done
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory
import org.eclipse.rdf4j.query.{GraphQuery, GraphQueryResult, Query, QueryInterruptedException, QueryResults, TupleQuery, TupleQueryResult}
import org.eclipse.rdf4j.rio.RDFWriterFactory

import java.io.OutputStream
import java.lang.AutoCloseable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait QueryEvaluator[Q <: Query] {
	/**
	 * Evaluates the query asynchronously, writing the results to `os`.
	 * The returned closer may be called from any thread, at any time, to abort the evaluation.
	 */
	def evaluate(query: Q, os: OutputStream)(using ExecutionContext): Try[(AutoCloseable, Future[Done])]
}

/**
 * Closes a query result that may not have been created yet.
 *
 * The query must be evaluated on the thread that iterates the result: LmdbRecordIterator only locks
 * against concurrent reads when it is closed from a thread other than the one that created it.
 * Evaluating on the caller's (Akka dispatcher) thread, and closing on the same dispatcher, would let
 * the close race with the reader and crash the JVM.
 */
private class ResultCloser[R <: AutoCloseable] extends AutoCloseable:
	private var result: Option[R] = None
	private var closed = false

	def close(): Unit = synchronized{closed = true; result}.foreach(_.close())

	def evaluate(create: => R)(report: R => Unit): Done =
		val res = create
		val wasClosed = synchronized:
			if !closed then result = Some(res)
			closed
		if wasClosed then
			res.close()
			throw QueryInterruptedException("SPARQL query was closed before evaluation started")
		report(res)
		Done


class TupleQueryEvaluator(wf: TupleQueryResultWriterFactory) extends QueryEvaluator[TupleQuery]:

	override def evaluate(query: TupleQuery, os: OutputStream)(using ExecutionContext): Try[(AutoCloseable, Future[Done])] =
		Try:
			val resultWriter = wf.getWriter(os)
			val closer = ResultCloser[TupleQueryResult]()
			closer -> Future:
				closer.evaluate(query.evaluate())(QueryResults.report(_, resultWriter))


class GraphQueryEvaluator(wf: RDFWriterFactory) extends QueryEvaluator[GraphQuery]:

	override def evaluate(query: GraphQuery, os: OutputStream)(using ExecutionContext): Try[(AutoCloseable, Future[Done])] =
		Try:
			val resultWriter = wf.getWriter(os)
			val closer = ResultCloser[GraphQueryResult]()
			closer -> Future:
				closer.evaluate(query.evaluate())(QueryResults.report(_, resultWriter))
