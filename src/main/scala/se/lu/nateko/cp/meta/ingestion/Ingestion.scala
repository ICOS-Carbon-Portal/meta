package se.lu.nateko.cp.meta.ingestion

import org.openrdf.model.ValueFactory
import org.openrdf.model.Statement
import se.lu.nateko.cp.meta.utils.sesame.Loading
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.ingestion.badm.BadmIngester

trait Ingester{
	def getStatements(valueFactory: ValueFactory): Iterator[Statement]
}

object Ingestion {

	def allIngesters: Map[String, Ingester] = {
		val (badmSchema, badm) = BadmIngester.getSchemaAndValuesIngesters
		Map(
			"manualContent" -> new RdfXmlFileIngester("/owl/cpmetainstances.owl"),
			"cpMetaOnto" -> new RdfXmlFileIngester("/owl/cpmeta.owl"),
			"stationEntryOnto" -> new RdfXmlFileIngester("/owl/stationEntry.owl"),
			"badm" -> badm,
			"badmSchema" -> badmSchema
		)
	}

	def ingest(target: InstanceServer, ingester: Ingester): Unit = {
		val newStatements = ingester.getStatements(target.factory)
		ingest(target, newStatements)
	}

	def ingest(target: InstanceServer, newStatements: Iterator[Statement]): Unit = {
		val newRepo = Loading.fromStatements(newStatements)
		val source = new SesameInstanceServer(newRepo)
		try{
			val updates = computeDiff(target, source).toIndexedSeq
			target.applyAll(updates)
		}finally{
			source.shutDown()
		}
	}

	def computeDiff(from: InstanceServer, to: InstanceServer): Seq[RdfUpdate] = {
		val toRemove = to.filterNotContainedStatements(from.getStatements(None, None, None))
		val toAdd = from.filterNotContainedStatements(to.getStatements(None, None, None))

		toRemove.map(RdfUpdate(_, false)) ++ toAdd.map(RdfUpdate(_, true))
	}

}