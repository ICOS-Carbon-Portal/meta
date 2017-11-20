package se.lu.nateko.cp.meta.services.sparql

import java.io.OutputStream

import org.eclipse.rdf4j.query.GraphQuery
import org.eclipse.rdf4j.query.Query
import org.eclipse.rdf4j.query.TupleQuery
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory
import org.eclipse.rdf4j.rio.RDFWriterFactory

trait QueryEvaluator[Q <: Query] {
	def evaluate(query: Q, os: OutputStream): Unit
}

class TupleQueryEvaluator(wf: TupleQueryResultWriterFactory) extends QueryEvaluator[TupleQuery]{

	override def evaluate(query: TupleQuery, os: OutputStream): Unit = {
		val resultWriter = wf.getWriter(os)
		query.evaluate(resultWriter)
	}
}

class GraphQueryEvaluator(wf: RDFWriterFactory) extends QueryEvaluator[GraphQuery]{

	override def evaluate(query: GraphQuery, os: OutputStream): Unit = {
		val resultWriter = wf.getWriter(os)
		query.evaluate(resultWriter)
	}
}
