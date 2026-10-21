package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, Resource}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.api.RdfLens.{DobjConn, DocConn, GlobConn, MetaConn}
import se.lu.nateko.cp.meta.api.{PidFactory, RdfLens, RdfLenses, SparqlRunner}
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.{CitableItem, EnvriConfigs, EnvriResolver, Licence, References, StaticCollection, StaticObject, collectionPrefix, objectPrefix}
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jTriplestoreConnection, StatementSource, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.upload.{StaticObjectReader, StaticObjectReferenceProvider}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.services.external.ExternalObjFetcher
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*

class CitationProvider(
	val repo: Repository,
	citClientFactory: List[Doi] => CitationClient,
	core: MetaCoreConfig,
	val lenses: RdfLenses,
	pidFactory: PidFactory,
	val externalObjs: ExternalObjFetcher,
)(using system: ActorSystem):
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

	val citer = new CitationMaker(doiCiter, vocab, metaVocab, core, externalObjs)

	val metaReader = StaticObjectReader(vocab, metaVocab, lenses, pidFactory, Some(citer), Some(externalObjs))

	def getCitation(res: Resource): Option[String] = access: conn ?=>
		given GlobConn = RdfLens.global(using conn)
		getDoiCitation(res).orElse:
			getCitableItem(res).flatMap(_.references.citationString)

	def getReferences(res: Resource): Option[References] = access:
		getCitableItem(res)(using RdfLens.global).map(_.references)

	/** References together with the status of the citation fetched from a DOI or an external repository.
	  * The errors are only those of making the citation info, as meta reports the errors of reading the object itself.
	  */
	def getReferencesWithRemoteCitation(res: Resource): Validated[(References, RemoteCitation)] = access: conn ?=>
		given GlobConn = RdfLens.global(using conn)
		val citationErrors = Seq.newBuilder[String]
		val recordingCiter = new StaticObjectReferenceProvider:
			def getItemCitationInfo(item: CitableItem): References = citer.getItemCitationInfo(item)
			def getCitationInfo(item: StaticObject, specConn: MetaConn)(using Envri, DocConn | DobjConn): Validated[References] =
				val refs = citer.getCitationInfo(item, specConn)
				citationErrors ++= refs.errors
				refs
		val reader = StaticObjectReader(vocab, metaVocab, lenses, pidFactory, Some(recordingCiter), Some(externalObjs))
		val found = for
			iri <- toIRI(res)
			given Envri <- inferObjectEnvri(iri).orElse(inferCollEnvri(iri))
			item <- getCitableItem(iri, reader)
		yield item.references -> citer.remoteCitation(item)
		new Validated(found, citationErrors.result())

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
			.flatten
	}

	private def getCitableItem(res: Resource, reader: StaticObjectReader = metaReader)(using GlobConn): Option[CitableItem] = toIRI(res).flatMap: iri =>
		if
			hasStatement(iri, RDF.TYPE, metaVocab.dataObjectClass) ||
			hasStatement(iri, RDF.TYPE, metaVocab.docObjectClass)
		then getStaticObject(iri, reader)
		else if
			hasStatement(iri, RDF.TYPE, metaVocab.collectionClass)
		then getStaticColl(iri, reader)
		else None

	private def toIRI(res: Resource): Option[IRI] = Option(res).collect{case iri: IRI => iri}

	private def getStaticObject(maybeDobj: IRI, reader: StaticObjectReader)(using GlobConn): Option[StaticObject] = for
		given Envri <- inferObjectEnvri(maybeDobj)
		obj <- reader.fetchStaticObject(maybeDobj).result
	yield obj

	private def getStaticColl(maybeColl: IRI, reader: StaticObjectReader)(using GlobConn): Option[StaticCollection] = for
		given Envri <- inferCollEnvri(maybeColl)
		coll <- reader.fetchStaticColl(maybeColl, None).result
	yield coll

	private def inferObjectEnvri(obj: IRI): Option[Envri] = EnvriResolver.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(objectPrefix(using envriConfs(envri)))
	}

	private def inferCollEnvri(obj: IRI): Option[Envri] = EnvriResolver.infer(obj.toJava).filter{
		envri => obj.stringValue.startsWith(collectionPrefix(using envriConfs(envri)))
	}

end CitationProvider
