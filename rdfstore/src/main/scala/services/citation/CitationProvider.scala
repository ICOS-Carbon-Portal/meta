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
import se.lu.nateko.cp.meta.api.{PidFactory, RdfLens, RdfLenses, SparqlRunner}
import se.lu.nateko.cp.meta.{CitationConfig, CpmetaConfig}
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.{CitableItem, EnvriConfigs, EnvriResolver, Licence, References, StaticCollection, StaticObject, collectionPrefix, objectPrefix}
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jTriplestoreConnection, StatementSource, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*

import CitationClient.CitationCache
import CitationClient.DoiCache

object CitationProvider:

	def apply(
		sail: Sail, citCache: CitationCache, doiCache: DoiCache, conf: CpmetaConfig
	)(using ActorSystem, Materializer): CitationProvider =
		apply(sail, citCache, doiCache, conf.core, conf.citations, CitationProviderConfig.getLenses(conf), CitationProviderConfig.pidFactory(conf))

	def apply(
		repo: Repository, citCache: CitationCache, doiCache: DoiCache, conf: CpmetaConfig
	)(using ActorSystem, Materializer): CitationProvider =
		apply(repo, citCache, doiCache, conf.core, conf.citations, CitationProviderConfig.getLenses(conf), CitationProviderConfig.pidFactory(conf))

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

end CitationProvider

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

	private def access[T](read: (TriplestoreConnection & SparqlRunner) ?=> T): T =
		Rdf4jTriplestoreConnection.access(repo)(read)

	val metaVocab = new CpmetaVocab(repo.getValueFactory)
	val vocab = new CpVocab(repo.getValueFactory)

	val doiCiter: CitationClient =
		val dois: List[Doi] = access:
			getStatements(null, metaVocab.hasDoi, null)
				.map(_.getObject.stringValue)
				.toList.distinct.flatMap:
					Doi.parse(_).toOption

		citClientFactory(dois)

	val citer = new CitationMaker(doiCiter, vocab, metaVocab, core)

	val metaReader = StaticObjectReader(vocab, metaVocab, lenses, pidFactory, Some(citer))

	def getCitation(res: Resource): Option[String] = access: conn ?=>
		given GlobConn = RdfLens.global(using conn)
		getDoiCitation(res).orElse:
			getCitableItem(res).result.flatMap(_.references.citationString)

	/** Empty result without errors if `res` is not a citable item; may have errors either way. */
	def getReferences(res: Resource): Validated[References] = access:
		getCitableItem(res)(using RdfLens.global).map(_.references)

	def getLicence(res: Resource): Option[Licence] = access: conn ?=>
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

	private def getCitableItem(res: Resource)(using GlobConn): Validated[CitableItem] = toIRI(res).fold(notFound): iri =>
		if
			hasStatement(iri, RDF.TYPE, metaVocab.dataObjectClass) ||
			hasStatement(iri, RDF.TYPE, metaVocab.docObjectClass)
		then getStaticObject(iri)
		else if
			hasStatement(iri, RDF.TYPE, metaVocab.collectionClass)
		then getStaticColl(iri)
		else notFound

	private def toIRI(res: Resource): Option[IRI] = Option(res).collect{case iri: IRI => iri}

	private def notFound: Validated[Nothing] = new Validated(None)

	private def getStaticObject(maybeDobj: IRI)(using GlobConn): Validated[StaticObject] =
		inferObjectEnvri(maybeDobj).fold(notFound): envri =>
			given Envri = envri
			metaReader.fetchStaticObject(maybeDobj)

	private def getStaticColl(maybeColl: IRI)(using GlobConn): Validated[StaticCollection] =
		inferCollEnvri(maybeColl).fold(notFound): envri =>
			given Envri = envri
			metaReader.fetchStaticColl(maybeColl, None)

	private def inferObjectEnvri(obj: IRI): Option[Envri] = EnvriResolver.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(objectPrefix(using envriConfs(envri)))
	}

	private def inferCollEnvri(obj: IRI): Option[Envri] = EnvriResolver.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(collectionPrefix(using envriConfs(envri)))
	}

end CitationProvider
