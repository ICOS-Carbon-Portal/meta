package se.lu.nateko.cp.meta.ingestion

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.vocabulary.LOCN
import se.lu.nateko.cp.meta.core.data.EnvriConfigs

import java.net.URI
import scala.concurrent.ExecutionContext

/**
 * meta-owned statement providers. Core ingestion machinery (`Ingester`, `Extractor`,
 * `Ingestion.ingest`, `BnodeStabilizers`, `RdfXmlFileIngester`, …) lives in rdf-common
 * so rdfStore can reuse it for its own (schema ontology) ingestion needs.
 */
object MetaIngestionProviders:

	def allProviders(using ActorSystem, ExecutionContext, Materializer, EnvriConfigs): Map[String, StatementProvider] =
		Map(
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
			"extraPeopleAndOrgs" -> new PeopleAndOrgsIngester("/extraPeople_3.csv"),

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
			"emptySource" -> Ingestion.EmptyIngester
		)
	end allProviders

end MetaIngestionProviders
