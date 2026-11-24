package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, Resource}
import org.eclipse.rdf4j.repository.Repository
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.api.{PidFactory, RdfLens, RdfLenses}
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.{CitableItem, EnvriConfigs, EnvriResolver, Licence, References, StaticCollection, StaticObject, collectionPrefix, objectPrefix}
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jInstanceServer, StatementSource}
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.services.external.ExternalObjFetcher
import se.lu.nateko.cp.meta.utils.rdf4j.*


class CitationProvider(
	val repo: Repository,
	citClientFactory: List[Doi] => CitationClient,
	core: MetaCoreConfig,
	val lenses: RdfLenses,
	pidFactory: PidFactory,
	val externalObjs: ExternalObjFetcher,
):
	private val log = LoggerFactory.getLogger(getClass())
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

	val citer = new CitationMaker(doiCiter, vocab, metaVocab, core, externalObjs)

	val metaReader = StaticObjectReader(vocab, metaVocab, lenses, pidFactory, citer)

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
			.flatten
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
