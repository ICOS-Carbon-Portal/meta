package se.lu.nateko.cp.meta.ingestion

import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.Statement
import se.lu.nateko.cp.meta.utils.sesame.Loading
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.ingestion.badm.BadmIngester
import org.eclipse.rdf4j.repository.Repository
import akka.actor.ActorSystem
import akka.stream.Materializer
import java.net.URI
import scala.reflect.ClassTag

sealed trait StatementProvider{
	def isAppendOnly: Boolean = false
}

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
			),
			"ingosPeopleAndOrgs" -> new PeopleAndOrgsIngester("/ingosPeopleAndOrgs.txt")
		)
	}

	def ingest(target: InstanceServer, ingester: Ingester, factory: ValueFactory): Unit =
		ingest[Ingester](target, ingester, _.getStatements(factory))

	def ingest(target: InstanceServer, extractor: Extractor, repo: Repository): Unit =
		ingest[Extractor](target, extractor, _.getStatements(repo))

	private def ingest[T <: StatementProvider](target: InstanceServer, provider: T, stFactory: T => Iterator[Statement]): Unit = {
		val newStatements = stFactory(provider)

		if(provider.isAppendOnly){
			val toAdd = target.filterNotContainedStatements(newStatements).map(RdfUpdate(_, true))
			target.applyAll(toAdd)
		} else {
			val newRepo = Loading.fromStatements(newStatements)
			val source = new SesameInstanceServer(newRepo)
			try{
				val updates = computeDiff(target.writeContextsView, source).toIndexedSeq
				target.applyAll(updates)
			}finally{
				source.shutDown()
			}
		}
	}

	private def computeDiff(from: InstanceServer, to: InstanceServer): Seq[RdfUpdate] = {
		val toRemove = to.filterNotContainedStatements(from.getStatements(None, None, None))
		val toAdd = from.filterNotContainedStatements(to.getStatements(None, None, None))

		toRemove.map(RdfUpdate(_, false)) ++ toAdd.map(RdfUpdate(_, true))
	}

}
