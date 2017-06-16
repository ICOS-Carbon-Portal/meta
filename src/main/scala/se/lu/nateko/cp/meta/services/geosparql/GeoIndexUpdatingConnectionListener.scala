package se.lu.nateko.cp.meta.services.geosparql

import org.eclipse.rdf4j.sail.SailConnectionListener
import org.eclipse.rdf4j.model.Statement


class GeoIndexUpdatingConnectionListener(geoIndex: IcosGeoIndex) extends SailConnectionListener {

	override def statementAdded(statement: Statement): Unit = {
		geoIndex.counter += 1
	}

	override def statementRemoved(statement: Statement): Unit = {
		geoIndex.counter -= 1
	}
}
