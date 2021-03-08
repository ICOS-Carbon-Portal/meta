package se.lu.nateko.cp.meta.services.citation

import akka.actor.ActorSystem
import akka.stream.Materializer

import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.CitableItem
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.{objectPrefix, collectionPrefix}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.upload.CollectionFetcher
import se.lu.nateko.cp.meta.services.upload.StaticObjectFetcher
import se.lu.nateko.cp.meta.services.upload.PlainStaticObjectFetcher
import se.lu.nateko.cp.meta.utils.rdf4j._

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.model.vocabulary.RDF

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
	private val server = new Rdf4jInstanceServer(repo)
	private val metaVocab = new CpmetaVocab(repo.getValueFactory)
	private val citer = new CitationMaker(doiCiter, repo, coreConf)

	private val (objFetcher, collFetcher) = {
		val pidFactory = new HandleNetClient.PidFactory(uploadConf.handle)
		val plainFetcher = new PlainStaticObjectFetcher(server)
		val collFetcher = new CollectionFetcher(server, plainFetcher, citer)
		val objFetcher = new StaticObjectFetcher(server, collFetcher, plainFetcher, pidFactory, citer)
		(objFetcher, collFetcher)
	}

	def getCitation(res: Resource): Option[String] = getDoiCitation(res).orElse{
		getCitableItem(res).flatMap(_.references.citationString)
	}

	def getReferences(res: Resource): Option[References] = getCitableItem(res).map(_.references)

	private def getDoiCitation(res: Resource): Option[String] = res match {
		case iri: IRI => server.getStringValues(iri, metaVocab.hasDoi).headOption
			.collect{ citer.extractDoiCitation(CitationStyle.TEXT) }
		case _ => None
	}

	private def getCitableItem(res: Resource): Option[CitableItem] = res match {

		case iri: IRI =>
			if(server.hasStatement(iri, RDF.TYPE, metaVocab.dataObjectClass) ||
				server.hasStatement(iri, RDF.TYPE, metaVocab.docObjectClass)
			) getStaticObject(iri)
			else if(server.hasStatement(iri, RDF.TYPE, metaVocab.collectionClass))
				getStaticColl(iri)
			else None
		case _ =>
			None
	}

	private def getStaticObject(maybeDobj: IRI): Option[StaticObject] = for(
		hash <- extractHash(maybeDobj);
		envri <- inferObjectEnvri(maybeDobj);
		obj <- objFetcher.fetch(hash)(envri)
	) yield obj

	private def getStaticColl(maybeColl: IRI): Option[StaticCollection] = for(
		hash <- extractHash(maybeColl);
		envri <- inferCollEnvri(maybeColl);
		coll <- collFetcher.fetchStatic(hash)(envri)
	) yield coll

	private def inferObjectEnvri(obj: IRI): Option[Envri] = Envri.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(objectPrefix(envriConfs(envri)))
	}

	private def inferCollEnvri(obj: IRI): Option[Envri] = Envri.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(collectionPrefix(envriConfs(envri)))
	}

	private def extractHash(iri: IRI): Option[Sha256Sum] =
		Sha256Sum.fromBase64Url(iri.getLocalName).toOption

}
