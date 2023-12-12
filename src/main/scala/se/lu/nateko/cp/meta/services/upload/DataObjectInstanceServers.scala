package se.lu.nateko.cp.meta.services.upload

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.api.RdfLenses
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObjectSpec
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.Station
import se.lu.nateko.cp.meta.instanceserver.CompositeReadonlyInstanceServer
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.InstanceServer.AtMostOne
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.utils.*

import scala.util.Success
import scala.util.Try


class DataObjectInstanceServers(
	repo: Repository,
	citationProvider: CitationProvider,
	val metaServers: Map[Envri, InstanceServer],
	val collectionServers: Map[Envri, InstanceServer],
	docServers: Map[Envri, InstanceServer],
	val allDataObjs: Map[Envri, InstanceServer],
	perFormat: Map[Envri, Map[IRI, InstanceServer]]
)(using envriConfs: EnvriConfigs, factory: ValueFactory){dois =>

	export citationProvider.{vocab, metaVocab, metaReader, lenses}
	val global = new Rdf4jInstanceServer(repo)

	def getObjSpecificationFormat(objectSpecification: IRI)(using Envri): TSC2V[IRI] =
		for
			metaLens <- lenses.metaInstanceLens
			formatIri <- getSingleUri(objectSpecification, metaVocab.hasFormat)(using metaLens)
		yield formatIri

	def getDataObjSpecification(specUri: IRI)(using Envri): TSC2V[DataObjectSpec] =
		for
			metaLens <- lenses.metaInstanceLens
			spec <- metaReader.getSpecification(specUri)(using metaLens)
		yield spec


	def getInstServerForFormat(format: IRI)(using envri: Envri): Validated[InstanceServer] =
		new Validated(perFormat.get(envri).flatMap(_.get(format))).require:
			s"ENVRI $envri unknown or has no instance server configured for data object format '$format'"

	def getInstServerForStaticObj(objHash: Sha256Sum)(using envri: Envri): Validated[InstanceServer] =
		global.access:
			val objIri = vocab.getStaticObject(objHash)
			if resourceHasType(objIri, metaVocab.docObjectClass)
			then docServer
			else metaReader.getObjFormatForDobj(objIri).flatMap(getInstServerForFormat)

	def getDocInstServer(using envri: Envri): Try[InstanceServer] = docServers.get(envri).toTry{
		new UploadUserErrorException(s"ENVRI '$envri' has no document instance server configured for it")
	}

	def getCollectionCreator(coll: Sha256Sum)(using Envri): Option[IRI] =
		collFetcherLite.flatMap(_.getCreatorIfCollExists(coll))

	def collectionExists(coll: Sha256Sum)(using Envri): Boolean =
		collFetcherLite.map(_.collectionExists(coll)).getOrElse(false)

	def collectionExists(coll: IRI)(using Envri): Boolean =
		collFetcherLite.map(_.collectionExists(coll)).getOrElse(false)

	def collFetcherLite(using envri: Envri): Option[CollectionFetcherLite] = collectionServers.get(envri)
		.map(new CollectionFetcherLite(_, vocab))

	def collectionServer(using envri: Envri): Validated[InstanceServer] =
		new Validated(collectionServers.get(envri)).require:
			s"ENVRI $envri or its 'instance server' for was not configured properly"

	def metaServer(using envri: Envri): Validated[InstanceServer] =
		new Validated(metaServers.get(envri)).require:
			s"ENVRI $envri or its 'instance server' for metadata was not configured properly"

	def docServer(using envri: Envri): Validated[InstanceServer] =
		new Validated(docServers.get(envri)).require:
			s"ENVRI $envri or its document 'instance server' was not configured properly"

	def getObjSubmitter(obj: ObjectUploadDto)(using envri: Envri): Option[IRI] = {
		val objUri = vocab.getStaticObject(obj.hashSum)
		val server = obj match{
			case _: DataObjectDto => allDataObjs(envri)
			case _: DocObjectDto => getDocInstServer.get
		}
		server.getUriValues(objUri, metaVocab.wasSubmittedBy).flatMap{subm =>
			server.getUriValues(subm, metaVocab.prov.wasAssociatedWith)
		}.headOption
	}
}
