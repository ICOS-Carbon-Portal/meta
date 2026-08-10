package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, Resource}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.sail.Sail
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.api.{HandleNetClient, RdfLens, RdfLenses}
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.{CitableItem, EnvriConfigs, EnvriResolver, Licence, References, StaticCollection, StaticObject, collectionPrefix, objectPrefix}
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jInstanceServer, StatementSource}
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.CitationConfig

import CitationClient.CitationCache
import CitationClient.DoiCache


object CitationProvider:
	def apply(
		sail: Sail, citCache: CitationCache, doiCache: DoiCache,
		core: MetaCoreConfig, citations: CitationConfig, lenses: RdfLenses, pidFactory: HandleNetClient.PidFactory
	)(using ActorSystem, Materializer): CitationProvider =
		val citClientFactory: List[Doi] => CitationClient =
			dois => CitationClientImpl(dois, citations, citCache, doiCache)
		new CitationProvider(sail, citClientFactory, core, lenses, pidFactory)

	def apply(
		repo: Repository, citCache: CitationCache, doiCache: DoiCache,
		core: MetaCoreConfig, citations: CitationConfig, lenses: RdfLenses, pidFactory: HandleNetClient.PidFactory
	)(using ActorSystem, Materializer): CitationProvider =
		val citClientFactory: List[Doi] => CitationClient =
			dois => CitationClientImpl(dois, citations, citCache, doiCache)
		new CitationProvider(repo, citClientFactory, core, lenses, pidFactory)


/**
 * Note: this takes already-resolved `MetaCoreConfig`/`RdfLenses`/`PidFactory` instead of the
 * whole `CpmetaConfig`, because `CpmetaConfig` (and the instance-server/upload-service
 * configuration it embeds) still lives in `rdfStore` until task 14 moves it to `rdfCommon`.
 * `CitationProvider` itself lives in `rdfCommon` now (task 11), so it cannot depend on a type
 * that is one layer further down the dependency graph; callers (in `meta`/`rdfStore`, which do
 * have `CpmetaConfig`) compute these values and pass them in. See docs/rdf-common-split/11-move-citation-stack.md.
 */
class CitationProvider(
	val repo: Repository,
	citClientFactory: List[Doi] => CitationClient,
	core: MetaCoreConfig,
	val lenses: RdfLenses,
	pidFactory: HandleNetClient.PidFactory,
)(using system: ActorSystem):
	def this(
		sail: Sail,
		citClientFactory: List[Doi] => CitationClient,
		core: MetaCoreConfig,
		lenses: RdfLenses,
		pidFactory: HandleNetClient.PidFactory,
	)(using ActorSystem) = this(new SailRepository(sail), citClientFactory, core, lenses, pidFactory)

	private val log = Logging.getLogger(system, this)
	import StatementSource.*
	private given envriConfs: EnvriConfigs = core.envriConfigs

	private val repositoryName = repo.getClass.getSimpleName
	log.info(s"Initializing $repositoryName...")
	repo.init()
	log.info(s"$repositoryName initialized")

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

	val citer = new CitationMaker(doiCiter, vocab, metaVocab, core)

	val metaReader = StaticObjectReader(vocab, metaVocab, lenses, pidFactory, Some(citer))

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
