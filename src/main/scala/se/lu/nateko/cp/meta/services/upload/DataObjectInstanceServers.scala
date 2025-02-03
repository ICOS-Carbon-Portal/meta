package se.lu.nateko.cp.meta.services.upload

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{DataObjectSpec, EnvriConfigs, Station}
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, Rdf4jInstanceServer, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.citation.{CitationMaker, CitationProvider}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab, MetadataException, UploadUserErrorException}
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.{DataObjectDto, DocObjectDto, ObjectUploadDto}

import scala.util.{Success, Try}


class DataObjectInstanceServers(
	val vanillaGlobal: InstanceServer,
	citationProvider: CitationProvider,
	metaServers: Map[Envri, InstanceServer],
	collectionServers: Map[Envri, InstanceServer],
	docServers: Map[Envri, InstanceServer],
	perFormat: Map[Envri, Map[IRI, InstanceServer]]
)(using envriConfs: EnvriConfigs, factory: ValueFactory):

	export citationProvider.{vocab, metaVocab, metaReader, lenses}
	import RdfLens.GlobConn

	def getInstServerForFormat(format: IRI)(using envri: Envri): Validated[InstanceServer] =
		new Validated(perFormat.get(envri).flatMap(_.get(format))).require:
			s"ENVRI $envri unknown or has no instance server configured for data object format '$format'"

	def getInstServerForStaticObj(objHash: Sha256Sum)(using Envri): Validated[InstanceServer] =
		vanillaGlobal.access: conn ?=>
			given GlobConn = RdfLens.global(using conn)
			val objIri = vocab.getStaticObject(objHash)
			if metaReader.docObjExists(objIri) then docServer
			else metaReader.getObjFormatForDobj(objIri).flatMap(getInstServerForFormat)

	def collectionServer(using envri: Envri): Validated[InstanceServer] =
		new Validated(collectionServers.get(envri)).require:
			s"ENVRI $envri or its 'instance server' for was not configured properly"

	def metaServer(using envri: Envri): Validated[InstanceServer] =
		new Validated(metaServers.get(envri)).require:
			s"ENVRI $envri or its 'instance server' for metadata was not configured properly"

	def docServer(using envri: Envri): Validated[InstanceServer] =
		new Validated(docServers.get(envri)).require:
			s"ENVRI $envri or its document 'instance server' was not configured properly"

end DataObjectInstanceServers
