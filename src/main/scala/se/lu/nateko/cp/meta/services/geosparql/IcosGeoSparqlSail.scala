package se.lu.nateko.cp.meta.services.geosparql

import org.eclipse.rdf4j.sail.NotifyingSail
import org.eclipse.rdf4j.sail.helpers.NotifyingSailWrapper
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.eclipse.rdf4j.query.algebra.evaluation.impl.ExtendedEvaluationStrategyFactory
import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunctionRegistry
import org.eclipse.rdf4j.query.algebra.evaluation.impl.AbstractEvaluationStrategyFactory

class IcosGeoSparqlSail(baseSail: MemoryStore) extends NotifyingSailWrapper(baseSail){

	private val geoIndex = new IcosGeoIndex

	//to enable "magic properties" support
	baseSail.setEvaluationStrategyFactory(
		new IcosGeoEvaluationStrategyFactory(geoIndex, baseSail.getFederatedServiceResolver())
	)

	override def getConnection() = new IcosGeoSparqlSailConnection(baseSail.getConnection, geoIndex)
}
