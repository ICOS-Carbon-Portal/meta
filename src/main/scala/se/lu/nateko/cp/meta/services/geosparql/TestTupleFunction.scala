package se.lu.nateko.cp.meta.services.geosparql

import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunction
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.query.QueryEvaluationException
import java.util.List
import scala.collection.JavaConverters._
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration

class TestTupleFunction(geoIndex: IcosGeoIndex) extends TupleFunction {

	override def getURI: String = GeoConstants.testFun

	override def evaluate(valueFactory: ValueFactory, args: Value*):
			CloseableIteration[_ <: List[_ <: Value], QueryEvaluationException] = {

		val res0 = new java.util.ArrayList[Literal](1)
		res0.add(valueFactory.createLiteral(geoIndex.counter))
		val res1 = new java.util.ArrayList[Value](1)
		res1.add(args(0))
		val iter = Iterator(res0, res1).asJava
		new CloseableIteratorIteration(iter)
	}
}
