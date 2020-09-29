package se.lu.nateko.cp.meta.services.citation

import akka.actor.ActorSystem
import akka.stream.Materializer

import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.core.data.objectPrefix
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.upload.CollectionFetcherLite
import se.lu.nateko.cp.meta.services.upload.StaticObjectFetcher
import se.lu.nateko.cp.meta.services.upload.PlainStaticObjectFetcher
import se.lu.nateko.cp.meta.utils.rdf4j._

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.Sail

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

		val doiCiter = new CitationClient(dois, conf.citations)
		new CitationProvider(doiCiter, sail, conf.core, conf.dataUploadService)
	}

}

class CitationProvider(val doiCiter: CitationClient, sail: Sail, coreConf: MetaCoreConfig, uploadConf: UploadServiceConfig){
	private implicit val envriConfs = coreConf.envriConfigs
	private val repo = new SailRepository(sail)
	private val citer = new CitationMaker(doiCiter, repo, coreConf)

	def getCitation(maybeDobj: Resource): Option[String] =
		getStaticObject(maybeDobj).flatMap(_.references.citationString)

	private val objFetcher = {
		val pidFactory = new HandleNetClient.PidFactory(uploadConf.handle)
		val server = new Rdf4jInstanceServer(repo)

		val collFetcher = new CollectionFetcherLite(server, citer.vocab)
		val plainFetcher = new PlainStaticObjectFetcher(server)
		new StaticObjectFetcher(server, collFetcher, plainFetcher, pidFactory, citer)
	}

	private def getStaticObject(maybeDobj: Resource): Option[StaticObject] = maybeDobj match {

		case iri: IRI => for(
			hash <- Sha256Sum.fromBase64Url(iri.getLocalName).toOption;
			envri <- inferObjectEnvri(iri);
			obj <- objFetcher.fetch(hash)(envri)
		) yield obj

		case _ =>
			None
	}

	private def inferObjectEnvri(obj: IRI): Option[Envri] = Envri.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(objectPrefix(envriConfs(envri)))
	}

}
