package se.lu.nateko.cp.meta.services.upload

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.api.RdfLenses
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObjectSpec
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.Station
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
	metaServers: Map[Envri, InstanceServer],
	collectionServers: Map[Envri, InstanceServer],
	docServers: Map[Envri, InstanceServer],
	perFormat: Map[Envri, Map[IRI, InstanceServer]]
)(using envriConfs: EnvriConfigs, factory: ValueFactory):

	export citationProvider.{vocab, metaVocab, metaReader, lenses}
	import RdfLens.GlobConn
	val global: InstanceServer = new Rdf4jInstanceServer(repo)

	def getInstServerForFormat(format: IRI)(using envri: Envri): Validated[InstanceServer] =
		new Validated(perFormat.get(envri).flatMap(_.get(format))).require:
			s"ENVRI $envri unknown or has no instance server configured for data object format '$format'"

	def getInstServerForStaticObj(objHash: Sha256Sum)(using Envri): Validated[InstanceServer] =
		global.access:
			given GlobConn = RdfLens.global
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
