package se.lu.nateko.cp.meta.datasets

import scala.util.Try
import scala.util.control.NoStackTrace

import org.openrdf.model.Literal
import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.vocabulary.XMLSchema

import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.ingestion.Vocab
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.sparqlserver.SparqlServer
import se.lu.nateko.cp.meta.utils.sesame._

class UploadService(server: InstanceServer, sparql: SparqlServer, conf: UploadServiceConfig) {

	private val factory = server.factory
	private val vocab = Vocab(factory)

	implicit def javaUriToSesame(uri: java.net.URI): URI = factory.createURI(uri)
	implicit def tupleToLiteral(tuple: (String, URI)): Literal = factory.createLiteral(tuple._1, tuple._2)

	def registerUpload(meta: UploadMetadataDto, uploader: UserInfo): Try[String] = Try{
		val submitter = meta.submitter
		val submitterConf = conf.submitters.get(submitter).getOrElse(
			throw new UploadUserErrorException(s"Unknown submitter: $submitter")
		)

		val userId = uploader.mail
		if(!submitterConf.authorizedUserIds.contains(userId))
			throw new UnauthorizedUploadException(s"User $userId is not authorized to upload on behalf of submitter $submitter")

		val datasetUri = factory.createURI(submitterConf.datasetClass, "/" + meta.hashSum)
		if(!server.getStatements(datasetUri).isEmpty)
			throw new UploadUserErrorException(s"Upload with hash sum ${meta.hashSum} has already been registered. Amendments are not supported yet!")

		val submissionUri = factory.createURI(vocab.submissionClass, "/" + meta.hashSum)
		val acquisitionUri = factory.createURI(vocab.acquisitionClass, "/" + meta.hashSum)

		//TODO Validate acquisition start/end datetimes
		//TODO Check that station, submitter, datastructure exist
		server.addAll(Seq[(URI, URI, Value)](

			(datasetUri, RDF.TYPE, submitterConf.datasetClass),
			(datasetUri, vocab.hasSha256sum, (meta.hashSum, XMLSchema.HEXBINARY)),
			(datasetUri, vocab.qb.structure, meta.dataStructure),
			(datasetUri, vocab.wasAcquiredBy, acquisitionUri),
			(datasetUri, vocab.wasSubmittedBy, submissionUri),

			(acquisitionUri, RDF.TYPE, vocab.acquisitionClass),
			(acquisitionUri, vocab.prov.startedAtTime, (meta.acquisitionStart, XMLSchema.DATETIME)),
			(acquisitionUri, vocab.prov.endedAtTime, (meta.acquisitionEnd, XMLSchema.DATETIME)),
			(acquisitionUri, vocab.prov.wasAssociatedWith, meta.station),

			(submissionUri, RDF.TYPE, vocab.submissionClass),
			(submissionUri, vocab.prov.startedAtTime, factory.getDateTimeNow),
			(submissionUri, vocab.prov.wasAssociatedWith, submitter)

		).map(factory.tripleToStatement))

		datasetUri.stringValue
	}
}

sealed abstract class UploadException(message: String) extends RuntimeException(message) with NoStackTrace

final class UploadUserErrorException(message: String) extends UploadException(message)
final class UnauthorizedUploadException(message: String) extends UploadException(message)