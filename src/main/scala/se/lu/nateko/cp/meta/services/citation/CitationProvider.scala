package se.lu.nateko.cp.meta.services.citation

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.Sail
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.CitableItem
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.Licence
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.core.data.collectionPrefix
import se.lu.nateko.cp.meta.core.data.objectPrefix
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.upload.CollectionFetcher
import se.lu.nateko.cp.meta.services.upload.PlainStaticObjectFetcher
import se.lu.nateko.cp.meta.services.upload.StaticObjectFetcher
import se.lu.nateko.cp.meta.utils.rdf4j.*
import CitationClient.CitationCache
import CitationClient.DoiCache

import java.net.URI
import scala.util.Using
import scala.concurrent.Future
import se.lu.nateko.cp.meta.services.upload.DoiService


type CitationProviderFactory = Sail => CitationProvider
object CitationProviderFactory{
	def apply(citCache: CitationCache, doiCache: DoiCache, conf: CpmetaConfig)(using ActorSystem, Materializer, Envri): CitationProviderFactory =
		sail => {
			val dois: List[Doi] = {
				val hasDoi = new CpmetaVocab(sail.getValueFactory).hasDoi
				Using(sail.getConnection){_
					.getStatements(null, hasDoi, null, false)
					.asPlainScalaIterator
					.map(_.getObject.stringValue)
					.toList.distinct.flatMap{
						Doi.parse(_).toOption
					}
				}.get
			}
			val doiCiter = CitationClientImpl(dois, conf, citCache, doiCache)
			CitationProvider(doiCiter, sail, conf.core, conf.dataUploadService)
		}
}

// trait CitationProvider{
// 	def doiCiter: CitationClient
// 	def metaVocab: CpmetaVocab
// 	def getCitation(res: Resource): Option[String]
// 	def getReferences(res: Resource): Option[References]
// 	def getLicence(res: Resource): Option[Licence]
// }

class CitationProvider(
	val doiCiter: CitationClient, sail: Sail, coreConf: MetaCoreConfig, uploadConf: UploadServiceConfig
){
	private given envriConfs: EnvriConfigs = coreConf.envriConfigs
	private val repo = new SailRepository(sail)
	private val server = new Rdf4jInstanceServer(repo)
	val metaVocab = new CpmetaVocab(repo.getValueFactory)
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

	def getLicence(res: Resource): Option[Licence] = for(
		iri <- toIRI(res);
		hash <- extractHash(iri);
		envri <- inferObjectEnvri(iri).orElse(inferCollEnvri(iri));
		lic <- citer.getLicence(hash)(envri)
	) yield lic

	private def getDoiCitation(res: Resource): Option[String] = toIRI(res).flatMap{iri =>
		server.getStringValues(iri, metaVocab.hasDoi).headOption
			.collect{ citer.extractDoiCitation(CitationStyle.HTML) }
	}

	private def getCitableItem(res: Resource): Option[CitableItem] = toIRI(res).flatMap{iri =>
		if(server.hasStatement(iri, RDF.TYPE, metaVocab.dataObjectClass) ||
			server.hasStatement(iri, RDF.TYPE, metaVocab.docObjectClass)
		) getStaticObject(iri)
		else if(server.hasStatement(iri, RDF.TYPE, metaVocab.collectionClass))
			getStaticColl(iri)
		else None
	}

	private def toIRI(res: Resource): Option[IRI] = Option(res).collect{case iri: IRI => iri}

	private def getStaticObject(maybeDobj: IRI): Option[StaticObject] = for(
		hash <- extractHash(maybeDobj);
		envri <- inferObjectEnvri(maybeDobj);
		obj <- objFetcher.fetch(hash)(using envri)
	) yield obj

	private def getStaticColl(maybeColl: IRI): Option[StaticCollection] = for(
		hash <- extractHash(maybeColl);
		envri <- inferCollEnvri(maybeColl);
		coll <- collFetcher.fetchStatic(hash)(envri)
	) yield coll

	private def inferObjectEnvri(obj: IRI): Option[Envri] = Envri.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(objectPrefix(using envriConfs(envri)))
	}

	private def inferCollEnvri(obj: IRI): Option[Envri] = Envri.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(collectionPrefix(using envriConfs(envri)))
	}

	private def extractHash(iri: IRI): Option[Sha256Sum] =
		Sha256Sum.fromBase64Url(iri.getLocalName).toOption
}
