package se.lu.nateko.cp.meta.services.sparql

import akka.Done
import org.eclipse.rdf4j.query.GraphQuery
import org.eclipse.rdf4j.query.Query
import org.eclipse.rdf4j.query.QueryResults
import org.eclipse.rdf4j.query.TupleQuery
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory
import org.eclipse.rdf4j.rio.RDFWriterFactory

import java.io.OutputStream
import java.lang.AutoCloseable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

trait QueryEvaluator[Q <: Query] {
	def evaluate(query: Q, os: OutputStream)(using ExecutionContext): Try[(AutoCloseable, Future[Done])]
}

class TupleQueryEvaluator(wf: TupleQueryResultWriterFactory) extends QueryEvaluator[TupleQuery]:

	override def evaluate(query: TupleQuery, os: OutputStream)(using ExecutionContext): Try[(AutoCloseable, Future[Done])] =
		Try:
			val resultWriter = wf.getWriter(os)
			val tupleRes = query.evaluate()
			tupleRes -> Future:
				QueryResults.report(tupleRes, resultWriter)
				Done


class GraphQueryEvaluator(wf: RDFWriterFactory) extends QueryEvaluator[GraphQuery]:

	override def evaluate(query: GraphQuery, os: OutputStream)(using ExecutionContext): Try[(AutoCloseable, Future[Done])] =
		Try:
			val resultWriter = wf.getWriter(os)
			val graphRes = query.evaluate()
			graphRes -> Future:
				QueryResults.report(graphRes, resultWriter)
				Done
