package se.lu.nateko.cp.meta.services.sparql.magic

import scala.collection.JavaConverters._

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.query.QueryEvaluationException
import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunction
import org.eclipse.rdf4j.sail.Sail

class DummyPlugin extends MagicTupleFuncPlugin {

	private var counter = 0

	def makeFunctions: Seq[TupleFunction] = {
		Seq(DummyTupleFunction)
	}

	def expressionEnricher = new SimpleTupleFunctionExprEnricher(Map(DummyTupleFunction.getURI -> DummyTupleFunction))

	def initialize(fromSail: Sail): Unit = {
		val conn = fromSail.getConnection
		try{
			val all = conn.getStatements(null, null, null, false)
			try{
				while(all.hasNext()){
					all.next()
					counter += 1
				}
			}finally{
				all.close()
			}
		}finally{
			conn.close()
		}
	}

	def statementAdded(s: Statement): Unit = {
		counter += 1
	}

	def statementRemoved(s: Statement): Unit = {
		counter -= 1
	}

	private object DummyTupleFunction extends TupleFunction {

		override def getURI: String = "http://meta.icos-cp.eu/ontologies/cpmeta/dummyMagicProp"

		override def evaluate(valueFactory: ValueFactory, args: Value*):
				CloseableIteration[_ <: java.util.List[_ <: Value], QueryEvaluationException] = {

			val res0 = new java.util.ArrayList[Literal](1)
			res0.add(valueFactory.createLiteral(counter))
			val res1 = new java.util.ArrayList[Value](1)
			res1.add(args(0))
			val iter = Iterator(res0, res1).asJava
			new CloseableIteratorIteration(iter)
		}
	}
}
