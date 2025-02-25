package se.lu.nateko.cp.meta.ingestion

import akka.actor.ActorSystem
import akka.stream.Materializer
import java.net.URI
import org.eclipse.rdf4j.model.vocabulary.LOCN
import org.eclipse.rdf4j.model.{Statement, ValueFactory}
import org.eclipse.rdf4j.repository.Repository
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.ingestion.Ingestion.Statements
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, Rdf4jInstanceServer, RdfUpdate}
import se.lu.nateko.cp.meta.utils.rdf4j.Loading

sealed trait StatementProvider{
	def isAppendOnly: Boolean = false
}

trait Ingester extends StatementProvider{
	def getStatements(valueFactory: ValueFactory): Ingestion.Statements
}

trait  Extractor extends StatementProvider{ self =>
	def getStatements(repo: Repository): Ingestion.Statements
	def map(ff: Repository => (Statement => Statement))(using ExecutionContext): Extractor = new Extractor{
		def getStatements(repo: Repository): Statements = self.getStatements(repo).map(_.mapC(ff(repo)))
	}
}

object Ingestion:

	type Statements = Future[CloseableIterator[Statement]]

	def allProviders(using ActorSystem, ExecutionContext, Materializer, EnvriConfigs): Map[String, StatementProvider] =
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
	end allProviders

	def ingest(
		target: InstanceServer, ingester: Ingester, factory: ValueFactory
	)(using ExecutionContext, BnodeStabilizers): Future[Int] =
		ingest[Ingester](target, ingester, _.getStatements(factory))

	def ingest(
		target: InstanceServer, extractor: Extractor, repo: Repository
	)(using ExecutionContext, BnodeStabilizers): Future[Int] =
		ingest[Extractor](target, extractor, _.getStatements(repo))

	/**
	  * Ingests statements from a provider into an InstanceServer. Blank nodes are
	  * "stabilized", i.e. converted to IRIs, using BnodeStabilizers. The statements are
	  * partitioned into IRI-subject ones and blank-node-subject ones. The former are
	  * placed first and sorted by subject, for reproducible order of occurrence of
	  * blank nodes in the object position. For stable minimal-change ingestions
	  * (best startup performance), the statement providers should strive to be deterministic.
	  *
	  * @param target
	  * @param provider
	  * @param stFactory
	  * @return `Future` with the number of updates applied (potentially including duplicate statements)
	  */
	private def ingest[T <: StatementProvider](
		target: InstanceServer, provider: T, stFactory: T => Statements
	)(using ExecutionContext, BnodeStabilizers): Future[Int] =
		stFactory(provider).map{rawStatements =>
			Using.Manager: use =>
				use.acquire(rawStatements)

				val rawStatsList = rawStatements.toIndexedSeq
				val (iriSubjs, bnodeSubjs) = rawStatsList.partition(_.getSubject.isIRI)
				val bnodeStabilizer = summon[BnodeStabilizers].getStabilizer(target.writeContext)
				val newStatements = (iriSubjs.sorted(using bySubjThenPred) ++ bnodeSubjs)
					.map(bnodeStabilizer.stabilizeBnodes(_, target.factory))

				val updates = if provider.isAppendOnly then
					target.filterNotContainedStatements(newStatements).map(RdfUpdate(_, true))
				else
					val newRepo = Loading.fromStatements(newStatements.iterator)
					val source = use(new Rdf4jInstanceServer(newRepo))
					computeDiff(target.writeContextsView, source)
				val sortedUpdates = updates.sortBy(_.statement)(using bySubjThenPred)
				target.applyAll(sortedUpdates)()
				sortedUpdates.length
			.get
		}

	val bySubjThenPred: Ordering[Statement] = Ordering.by[Statement, String](_.getSubject.stringValue)
		.orElseBy(_.getPredicate.stringValue)

	private def computeDiff(from: InstanceServer, to: InstanceServer): IndexedSeq[RdfUpdate] = {
		val toRemove = to.filterNotContainedStatements(from.getStatements(None, None, None))
		val toAdd = from.filterNotContainedStatements(to.getStatements(None, None, None))

		toRemove.map(RdfUpdate(_, false)) ++ toAdd.map(RdfUpdate(_, true))
	}

	object EmptyIngester extends Ingester{
		override def getStatements(valueFactory: ValueFactory): Statements = Future.successful(CloseableIterator.empty)
	}


end Ingestion
