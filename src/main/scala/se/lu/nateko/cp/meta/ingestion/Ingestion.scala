package se.lu.nateko.cp.meta.ingestion

import org.openrdf.model.ValueFactory
import org.openrdf.model.Statement
import se.lu.nateko.cp.meta.utils.sesame.Loading
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.ingestion.badm.BadmIngester
import org.openrdf.repository.Repository
import akka.actor.ActorSystem
import akka.stream.Materializer
import java.net.URI

sealed trait StatementProvider

trait Ingester extends StatementProvider{
	def getStatements(valueFactory: ValueFactory): Iterator[Statement]
}

trait Extractor extends StatementProvider{
	def getStatements(repo: Repository): Iterator[Statement]
}

object Ingestion {

	def allProviders(implicit system: ActorSystem, mat: Materializer): Map[String, StatementProvider] = {
		val (badmSchema, badm) = BadmIngester.getSchemaAndValuesIngesters
		Map(
			"cpMetaOnto" -> new RdfXmlFileIngester("/owl/cpmeta.owl"),
			"stationEntryOnto" -> new RdfXmlFileIngester("/owl/stationEntry.owl"),
			"badm" -> badm,
			"badmSchema" -> badmSchema,
			"pisAndStations" -> new SparqlConstructExtractor("/sparql/labelingToCpOnto.txt"),
			"cpMetaInstances" -> new RemoteRdfGraphIngester(
				endpoint = new URI("https://meta.icos-cp.eu/sparql"),
				rdfGraph = new URI("http://meta.icos-cp.eu/resources/cpmeta/")
			)
		)
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