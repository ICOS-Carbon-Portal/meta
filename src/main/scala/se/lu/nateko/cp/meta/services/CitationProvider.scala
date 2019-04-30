package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.sail.Sail
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.api.CitationClient
import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.Resource
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.instanceserver.Rdf4jSailInstanceServer
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.services.upload.CollectionFetcherLite
import se.lu.nateko.cp.meta.services.upload.StaticObjectFetcher
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.api.Doi
import se.lu.nateko.cp.meta.views.LandingPageHelpers
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.CpmetaConfig
import org.eclipse.rdf4j.model.Statement
import se.lu.nateko.cp.meta.utils.rdf4j._

class CitationProviderFactory(conf: CpmetaConfig)(implicit system: ActorSystem, mat: Materializer){

	def getProvider(sail: Sail): CitationProvider = {

		val dois: List[Doi] = {
			val hasDoi = new CpmetaVocab(sail.getValueFactory).hasDoi
			sail
				.access[Statement]{conn =>
					conn.getStatements(null, hasDoi, null, false)
				}
				.map(_.getObject.stringValue)
				.toList.distinct.collect{
					case Doi(doi) => doi
				}
		}

		val dataCiter = new CitationClient(dois, conf.citations)
		new CitationProvider(dataCiter, sail, conf.core, conf.dataUploadService)
	}


}

class CitationProvider(val dataCiter: CitationClient, sail: Sail, coreConf: MetaCoreConfig, uploadConf: UploadServiceConfig){
	val vocab = new CpVocab(sail.getValueFactory)(coreConf.envriConfigs)

	def getCitation(maybeDobj: Resource): Option[String] = getDataObject(maybeDobj).flatMap{dobj =>
		getIcosCitation(dobj).orElse(getDataCiteCitation(dobj))
	}

	private implicit val envri = Envri.ICOS //functionality limited to ICOS for now

	private val objFetcher = {
		val pidFactory = new HandleNetClient.PidFactory(uploadConf.handle)
		val server = new Rdf4jSailInstanceServer(sail)

		val collFetcher = new CollectionFetcherLite(server, vocab)
		new StaticObjectFetcher(server, vocab, collFetcher, pidFactory)
	}

	private val objPrefix: String = vocab.staticObjectPrefix

	def getDataObject(maybeDobj: Resource): Option[DataObject] = maybeDobj match{

		case iri: IRI if iri.stringValue.startsWith(objPrefix) => for(
			hash <- Sha256Sum.fromBase64Url(iri.getLocalName).toOption;
			dobj <- objFetcher.fetch(hash).collect{
				case data: DataObject => data
			}
		) yield dobj

		case _ =>
			None
	}

	def getDataCiteCitation(dobj: DataObject): Option[String] = for(
		doiStr <- dobj.doi;
		doi <- Doi.unapply(doiStr);
		//_ = println("looking up DataCite citation for " + doiStr);
		citFut = dataCiter.getCitation(doi);
		cit <- citFut.value.flatMap(_.toOption)
	) yield cit

	def getIcosCitation(dobj: DataObject): Option[String] =
		LandingPageHelpers.icosCitation(dobj, vocab, coreConf.handleService)
}
