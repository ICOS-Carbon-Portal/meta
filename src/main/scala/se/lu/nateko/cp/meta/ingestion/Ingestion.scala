package se.lu.nateko.cp.meta.ingestion

import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.Statement
import se.lu.nateko.cp.meta.utils.rdf4j.Loading
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import org.eclipse.rdf4j.repository.Repository
import akka.actor.ActorSystem
import akka.stream.Materializer
import java.net.URI

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import scala.util.Using
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.ingestion.Ingestion.Statements
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import org.eclipse.rdf4j.model.vocabulary.LOCN
import org.eclipse.rdf4j.model.Literal

sealed trait StatementProvider{
	def isAppendOnly: Boolean = false
}

trait Ingester extends StatementProvider{
	def getStatements(valueFactory: ValueFactory): Ingestion.Statements
}

trait  Extractor extends StatementProvider{ self =>
	def getStatements(repo: Repository): Ingestion.Statements
	def map(ff: Repository => (Statement => Statement))(using ExecutionContext) = new Extractor{
		def getStatements(repo: Repository): Statements = self.getStatements(repo).map(_.mapC(ff(repo)))
	}
}

object Ingestion {

	type Statements = Future[CloseableIterator[Statement]]

	def allProviders(using system: ActorSystem, mat: Materializer, envries: EnvriConfigs): Map[String, StatementProvider] = {
		import system.dispatcher
		Map(
			"cpMetaOnto" -> new RdfXmlFileIngester("/owl/cpmeta.owl"),
			"otcMetaOnto" -> new RdfXmlFileIngester("/owl/otcmeta.owl"),
			"stationEntryOnto" -> new RdfXmlFileIngester("/owl/stationEntry.owl"),
			"extraStations" -> new ExtraStationsIngester("/extraStations.csv"),
			"cpMetaInstances" -> new RemoteRdfGraphIngester(
				endpoint = new URI("https://meta.icos-cp.eu/sparql"),
				rdfGraph = new URI("http://meta.icos-cp.eu/resources/cpmeta/")
			),
			"cpMetaCityInstances" -> new RemoteRdfGraphIngester(
				endpoint = new URI("https://citymeta.icos-cp.eu/sparql"),
				rdfGraph = new URI("https://citymeta.icos-cp.eu/resources/cpmeta/")
			),
			"icosInstances" -> new RemoteRdfGraphIngester(
				endpoint = new URI("https://meta.icos-cp.eu/sparql"),
				rdfGraph = new URI("http://meta.icos-cp.eu/resources/icos/")
			),
			"sitesMetaInstances" -> new RemoteRdfGraphIngester(
				endpoint = new URI("https://meta.icos-cp.eu/sparql"),
				rdfGraph = new URI("https://meta.fieldsites.se/resources/sites/")
			),
			"otcMetaEntry" -> new RemoteRdfGraphIngester(
				endpoint = new URI("https://meta.icos-cp.eu/sparql"),
				rdfGraph = new URI("http://meta.icos-cp.eu/resources/otcmeta/")
			),
//			"cpStationEntry" -> new RemoteRdfGraphIngester(
//				endpoint = new URI("https://meta.icos-cp.eu/sparql"),
//				rdfGraph = new URI("http://meta.icos-cp.eu/resources/stationentry/")
//			),
			"extraPeopleAndOrgs" -> new PeopleAndOrgsIngester("/extraPeopleAndOrgs_2.txt"),

			"dcatdemo" -> new LocalSparqlConstructExtractor(
				"/sparql/cpL2ToDcat.rq", "/sparql/cpToEnvriSiteDocUseCase_1.rq", "/sparql/cpToEnvriSiteDocUseCase_2.rq"
			).map{repo =>
				val vf = repo.getValueFactory
				val geoSparqlLitType = vf.createIRI("http://www.opengis.net/ont/geosparql/geoJSONLiteral")
				st =>
					val pred = st.getPredicate
					if pred == LOCN.GEOMETRY_PROP then
						val typedLit = vf.createLiteral(st.getObject.stringValue, geoSparqlLitType)
						vf.createStatement(st.getSubject, pred, typedLit, st.getContext)
					else st
			},
			"emptySource" -> EmptyIngester
		)
	}

	def ingest(target: InstanceServer, ingester: Ingester, factory: ValueFactory)(implicit ctxt: ExecutionContext): Future[Unit] =
		ingest[Ingester](target, ingester, _.getStatements(factory))

	def ingest(target: InstanceServer, extractor: Extractor, repo: Repository)(implicit ctxt: ExecutionContext): Future[Unit] =
		ingest[Extractor](target, extractor, _.getStatements(repo))

	private def ingest[T <: StatementProvider](
			target: InstanceServer,
			provider: T, stFactory: T => Statements
	)(implicit ctxt: ExecutionContext): Future[Unit] = stFactory(provider).map{newStatements =>
		Using.Manager{use =>
			use.acquire(newStatements)
			if(provider.isAppendOnly){
				val toAdd = target.filterNotContainedStatements(newStatements).map(RdfUpdate(_, true))
				target.applyAll(toAdd)()
			} else {
				val newRepo = Loading.fromStatements(newStatements)
				val source = use(new Rdf4jInstanceServer(newRepo))
				val updates = computeDiff(target.writeContextsView, source)
				target.applyAll(updates)()
			}
		}
	}

	private def computeDiff(from: InstanceServer, to: InstanceServer): IndexedSeq[RdfUpdate] = {
		val toRemove = to.filterNotContainedStatements(from.getStatements(None, None, None))
		val toAdd = from.filterNotContainedStatements(to.getStatements(None, None, None))

		toRemove.map(RdfUpdate(_, false)) ++ toAdd.map(RdfUpdate(_, true))
	}

	object EmptyIngester extends Ingester{
		override def getStatements(valueFactory: ValueFactory) = Future.successful(CloseableIterator.empty)
	}

}
