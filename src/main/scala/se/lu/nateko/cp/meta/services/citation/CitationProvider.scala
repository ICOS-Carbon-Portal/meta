package se.lu.nateko.cp.meta.services.citation

import akka.actor.ActorSystem
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.Sail
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.core.data.CitableItem
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.EnvriResolver
import se.lu.nateko.cp.meta.core.data.Licence
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.core.data.collectionPrefix
import se.lu.nateko.cp.meta.core.data.objectPrefix
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.utils.rdf4j.*
import CitationClient.CitationCache
import CitationClient.DoiCache
import akka.event.Logging


object CitationProvider:
	def apply(
		sail: Sail, citCache: CitationCache, doiCache: DoiCache, conf: CpmetaConfig
	)(using ActorSystem, Materializer): CitationProvider =
		val citClientFactory: List[Doi] => CitationClient =
			dois => CitationClientImpl(dois, conf.citations, citCache, doiCache)
		new CitationProvider(sail, citClientFactory, conf)


class CitationProvider(
	sail: Sail,
	citClientFactory: List[Doi] => CitationClient,
	conf: CpmetaConfig,
)(using system: ActorSystem):
	private val log = Logging.getLogger(system, this)
	import TriplestoreConnection.*
	private given envriConfs: EnvriConfigs = conf.core.envriConfigs

	val repo = new SailRepository(sail)
	private val sailName = sail.getClass.getSimpleName
	log.info(s"Initializing $sailName SailRepository...")
	repo.init()
	log.info(s"$sailName initialized")

	val server = new Rdf4jInstanceServer(repo)
	val metaVocab = new CpmetaVocab(repo.getValueFactory)
	val vocab = new CpVocab(repo.getValueFactory)

	val doiCiter: CitationClient =
		val dois: List[Doi] = server.access:
			getStatements(null, metaVocab.hasDoi, null)
				.map(_.getObject.stringValue)
				.toList.distinct.flatMap:
					Doi.parse(_).toOption

		citClientFactory(dois)

	val citer = new CitationMaker(doiCiter, vocab, metaVocab, conf.core)

	val lenses = MetaDb.getLenses(conf.instanceServers, conf.dataUploadService)

	val metaReader =
		val pidFactory = new HandleNetClient.PidFactory(conf.dataUploadService.handle)
		StaticObjectReader(vocab, metaVocab, lenses, pidFactory, citer)

	def getCitation(res: Resource): Option[String] = server.access: conn ?=>
		given GlobConn = RdfLens.global(using conn)
		getDoiCitation(res).orElse:
			getCitableItem(res).flatMap(_.references.citationString)

	def getReferences(res: Resource): Option[References] = server.access:
		getCitableItem(res)(using RdfLens.global).map(_.references)

	def getLicence(res: Resource): Option[Licence] = server.access: conn ?=>
		for
			iri <- toIRI(res)
			given Envri <- inferObjectEnvri(iri).orElse(inferCollEnvri(iri))
			given GlobConn = RdfLens.global(using conn)
			lic <- citer.getLicence(iri).result
		yield lic

	private def getDoiCitation(res: Resource)(using GlobConn): Option[String] = toIRI(res).flatMap{iri =>
		getStringValues(iri, metaVocab.hasDoi).headOption
			.collect{ citer.extractDoiCitation(CitationStyle.HTML) }
	}

	private def getCitableItem(res: Resource)(using GlobConn): Option[CitableItem] = toIRI(res).flatMap: iri =>
		if
			hasStatement(iri, RDF.TYPE, metaVocab.dataObjectClass) ||
			hasStatement(iri, RDF.TYPE, metaVocab.docObjectClass)
		then getStaticObject(iri)
		else if
			hasStatement(iri, RDF.TYPE, metaVocab.collectionClass)
		then getStaticColl(iri)
		else None

	private def toIRI(res: Resource): Option[IRI] = Option(res).collect{case iri: IRI => iri}

	private def getStaticObject(maybeDobj: IRI)(using GlobConn): Option[StaticObject] = for
		given Envri <- inferObjectEnvri(maybeDobj)
		obj <- metaReader.fetchStaticObject(maybeDobj).result
	yield obj

	private def getStaticColl(maybeColl: IRI)(using GlobConn): Option[StaticCollection] = for
		given Envri <- inferCollEnvri(maybeColl)
		coll <- metaReader.fetchStaticColl(maybeColl, None).result
	yield coll

	private def inferObjectEnvri(obj: IRI): Option[Envri] = EnvriResolver.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(objectPrefix(using envriConfs(envri)))
	}

	private def inferCollEnvri(obj: IRI): Option[Envri] = EnvriResolver.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(collectionPrefix(using envriConfs(envri)))
	}

end CitationProvider
