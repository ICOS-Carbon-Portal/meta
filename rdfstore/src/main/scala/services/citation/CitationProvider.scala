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
import se.lu.nateko.cp.meta.api.{PidFactory, RdfLens, RdfLenses}
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

	/**
	 * `CitationStoreConfig` is rdfStore's own narrow view of the shared `cpmeta` config section
	 * (see `CitationStoreConfig`'s scaladoc); this overload does the config -> RdfLenses/PidFactory
	 * translation and defers to the resolved-primitives overload below.
	 */
	def apply(
		sail: Sail, citCache: CitationCache, doiCache: DoiCache, conf: CitationStoreConfig
	)(using ActorSystem, Materializer): CitationProvider =
		apply(sail, citCache, doiCache, conf.core, conf.citations, getLenses(conf.citationGraphs), pidFactory(conf))

	def apply(
		repo: Repository, citCache: CitationCache, doiCache: DoiCache, conf: CitationStoreConfig
	)(using ActorSystem, Materializer): CitationProvider =
		apply(repo, citCache, doiCache, conf.core, conf.citations, getLenses(conf.citationGraphs), pidFactory(conf))

	def apply(
		sail: Sail, citCache: CitationCache, doiCache: DoiCache,
		core: MetaCoreConfig, citations: CitationConfig, lenses: RdfLenses, pidFactory: PidFactory
	)(using ActorSystem, Materializer): CitationProvider =
		val citClientFactory: List[Doi] => CitationClient =
			dois => CitationClientImpl(dois, citations, citCache, doiCache)
		new CitationProvider(sail, citClientFactory, core, lenses, pidFactory)

	def apply(
		repo: Repository, citCache: CitationCache, doiCache: DoiCache,
		core: MetaCoreConfig, citations: CitationConfig, lenses: RdfLenses, pidFactory: PidFactory
	)(using ActorSystem, Materializer): CitationProvider =
		val citClientFactory: List[Doi] => CitationClient =
			dois => CitationClientImpl(dois, citations, citCache, doiCache)
		new CitationProvider(repo, citClientFactory, core, lenses, pidFactory)

	def pidFactory(conf: CitationStoreConfig): PidFactory =
		val handleConf = conf.handle
		new PidFactory(handleConf.baseUrl, handleConf.prefix)

	def getLenses(graphs: CitationGraphsConfig): RdfLenses =
		def graphLenses[L](confs: Map[Envri, ReadGraphConfig], factory: (java.net.URI, Seq[java.net.URI]) => L): Map[Envri, L] =
			confs.map { case (envri, conf) => envri -> factory(conf.primaryContext, conf.readContexts) }

		val perFormat = graphs.dataObjects.map: (envri, config) =>
			val lenses = config.definitions.map[(java.net.URI, RdfLens.DobjLens)]: definition =>
				val primaryContext = config.primaryContext(definition)
				definition.format -> RdfLens.dobjLens(primaryContext, primaryContext +: config.commonReadContexts)
			envri -> lenses.toMap

		// metaInstances/cpMetaInstances are intentionally empty: `RdfLenses.metaInstanceLens` and
		// `.cpLens` have no rdfStore call site (task 25), they only serve `meta`'s upload,
		// linked-data and metaflow paths. The aggregate type is still shared, so the two
		// categories are passed empty rather than removed from `RdfLenses`.
		RdfLenses(
			metaInstances = Map.empty,
			cpMetaInstances = Map.empty,
			collections = graphLenses(graphs.collections, RdfLens.collLens),
			documents = graphLenses(graphs.documents, RdfLens.docLens),
			dobjPerFormat = perFormat
		)

end CitationProvider

/**
 * rdfStore-owned (task 24: derived-metadata ownership moved here from `rdf-common`, reversing
 * an earlier plan - task 11 - to share this class with `meta`; `meta` now reads citation/licence
 * data through the HTTP `DerivedMetadataClient` instead of constructing its own provider). The
 * companion object's `CitationStoreConfig` overloads above are the only construction path in
 * practice; the `MetaCoreConfig`/`CitationConfig`/`RdfLenses`/`PidFactory` overloads exist mainly
 * so tests (`TestDb.scala`) can supply hand-built fixtures without a full `CitationStoreConfig`.
 */
class CitationProvider(
	val repo: Repository,
	citClientFactory: List[Doi] => CitationClient,
	core: MetaCoreConfig,
	val lenses: RdfLenses,
	pidFactory: PidFactory,
)(using system: ActorSystem):
	def this(
		sail: Sail,
		citClientFactory: List[Doi] => CitationClient,
		core: MetaCoreConfig,
		lenses: RdfLenses,
		pidFactory: PidFactory,
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
