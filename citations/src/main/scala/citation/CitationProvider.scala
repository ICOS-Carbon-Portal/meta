package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, Resource}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.api.{HandleNetClient, RdfLens}
import se.lu.nateko.cp.meta.core.data.{CitableItem, EnvriConfigs, EnvriResolver, Licence, References, StaticCollection, StaticObject, collectionPrefix, objectPrefix}
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jInstanceServer, StatementSource, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.{CpmetaConfig, MetaDb}

import CitationClient.CitationCache
import CitationClient.DoiCache


object CitationProvider:
	def apply(
		repo: Repository, citCache: CitationCache, doiCache: DoiCache, conf: CpmetaConfig
	)(using ActorSystem, Materializer): CitationProvider =
		val citClientFactory: List[Doi] => CitationClient =
			dois => CitationClientImpl(dois, conf.citations, citCache, doiCache)
		new CitationProvider(repo, citClientFactory, conf)


class CitationProvider(
	val repo: Repository,
	citClientFactory: List[Doi] => CitationClient,
	conf: CpmetaConfig,
)(using system: ActorSystem):
	private val log = Logging.getLogger(system, this)
	import StatementSource.*
	private given envriConfs: EnvriConfigs = conf.core.envriConfigs

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

	val citer = new LiveCitationMaker(doiCiter, vocab, metaVocab, conf.core)

	val lenses = MetaDb.getLenses(conf.instanceServers, conf.dataUploadService)

	val metaReader =
		val pidFactory = new HandleNetClient.PidFactory(conf.dataUploadService.handle)
		StaticObjectReader(vocab, metaVocab, lenses, pidFactory, citer)

	/** Freshly computes the static object behind a landing-page URI (used by the
	 *  citations service's HTTP endpoint that serves meta's DOI-minting path). */
	def fetchFreshObject(uri: java.net.URI): Option[StaticObject] = server.access: conn ?=>
		given GlobConn = RdfLens.global(using conn)
		getStaticObject(repo.getValueFactory.createIRI(uri.toString))

	def fetchFreshCollection(uri: java.net.URI): Option[StaticCollection] = server.access: conn ?=>
		given GlobConn = RdfLens.global(using conn)
		getStaticColl(repo.getValueFactory.createIRI(uri.toString))

	def getCitation(res: Resource): Option[String] = server.access: conn ?=>
		getCitation(res, conn)

	def getReferences(res: Resource): Option[References] = server.access: conn ?=>
		getReferences(res, conn)

	def getLicence(res: Resource): Option[Licence] = server.access: conn ?=>
		getLicence(res, conn)

	def getCitation(res: Resource, conn: TriplestoreConnection): Option[String] =
		given GlobConn = RdfLens.global(using conn)
		getDoiCitation(res).orElse:
			getCitableItem(res).flatMap(_.references.citationString)

	def getReferences(res: Resource, conn: TriplestoreConnection): Option[References] =
		getCitableItem(res)(using RdfLens.global(using conn)).map(_.references)

	def getLicence(res: Resource, conn: TriplestoreConnection): Option[Licence] =
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
