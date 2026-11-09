package se.lu.nateko.cp.meta.services.sparql

import scala.language.unsafeNulls

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory
import org.eclipse.rdf4j.query.{GraphQuery, Query, TupleQuery}
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.rio.RDFWriterFactory

import java.io.OutputStream

import QueryStreaming.ResultWriting

trait QueryEvaluator[Q <: Query] {
	def evaluate(query: Q, os: OutputStream): ResultWriting[?]

	/** See QueryStreaming.source */
	def source(
		getConnection: () => RepositoryConnection,
		queryStr: String,
		cancellation: QueryCancellation,
		onFinish: () => Unit
	): Source[ByteString, NotUsed] = QueryStreaming.source(this, getConnection, queryStr, cancellation, onFinish)
}

class TupleQueryEvaluator(wf: TupleQueryResultWriterFactory) extends QueryEvaluator[TupleQuery] {

	override def evaluate(query: TupleQuery, os: OutputStream): ResultWriting[?] = {
		val resultWriter = wf.getWriter(os)
		val tupleRes = query.evaluate()
		ResultWriting(tupleRes, resultWriter.startQueryResult(tupleRes.getBindingNames()), resultWriter.handleSolution, resultWriter.endQueryResult)
	}
}


class GraphQueryEvaluator(wf: RDFWriterFactory) extends QueryEvaluator[GraphQuery] {

	override def evaluate(query: GraphQuery, os: OutputStream): ResultWriting[?] = {
		val resultWriter = wf.getWriter(os)
		val graphRes = query.evaluate()
		ResultWriting(
			graphRes,
			{
				resultWriter.startRDF()
				graphRes.getNamespaces().forEach(resultWriter.handleNamespace)
			},
			resultWriter.handleStatement,
			resultWriter.endRDF
		)
	}
}
