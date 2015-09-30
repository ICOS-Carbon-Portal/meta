package se.lu.nateko.cp.meta.services

import scala.util.Try
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
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.utils.DateTimeUtils

class UploadService(server: InstanceServer, conf: UploadServiceConfig) {

	private val factory = server.factory
	private val vocab = Vocab(factory)

	implicit def javaUriToSesame(uri: java.net.URI): URI = factory.createURI(uri)

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

		if(server.getStatements(Some(meta.station), Some(RDF.TYPE), Some(submitterConf.stationClass)).isEmpty)
			throw new UploadUserErrorException(s"Unknown station: ${meta.station}")

		if(server.getStatements(Some(meta.dataStructure), Some(RDF.TYPE), Some(submitterConf.dataStructureClass)).isEmpty)
			throw new UploadUserErrorException(s"Unknown data structure: ${meta.dataStructure}")

		val submissionUri = factory.createURI(vocab.submissionClass, "/" + meta.hashSum)
		val acquisitionUri = factory.createURI(vocab.acquisitionClass, "/" + meta.hashSum)

		server.addAll(Seq[(URI, URI, Value)](

			(datasetUri, RDF.TYPE, submitterConf.datasetClass),
			(datasetUri, vocab.hasSha256sum, makeSha256Literal(meta.hashSum)),
			(datasetUri, vocab.qb.structure, meta.dataStructure),
			(datasetUri, vocab.wasAcquiredBy, acquisitionUri),
			(datasetUri, vocab.wasSubmittedBy, submissionUri),

			(acquisitionUri, RDF.TYPE, vocab.acquisitionClass),
			(acquisitionUri, vocab.prov.startedAtTime, makeDateTimeLiteral(meta.acquisitionStart)),
			(acquisitionUri, vocab.prov.endedAtTime, makeDateTimeLiteral(meta.acquisitionEnd)),
			(acquisitionUri, vocab.prov.wasAssociatedWith, meta.station),

			(submissionUri, RDF.TYPE, vocab.submissionClass),
			(submissionUri, vocab.prov.startedAtTime, factory.getDateTimeNow),
			(submissionUri, vocab.prov.wasAssociatedWith, submitter)

		).map(factory.tripleToStatement))

		datasetUri.stringValue
	}

	import UploadService._

	private def makeDateTimeLiteral(dt: String): Literal =
		factory.createLiteral(dateTimeToUtc(dt), XMLSchema.DATETIME)

	private def makeSha256Literal(sum: String): Literal =
		factory.createLiteral(ensureSha256(sum), XMLSchema.HEXBINARY)
}

object UploadService{

	def dateTimeToUtc(dt: String): String = 
		try{
			val parsed = DateTimeUtils.defaultFormatter.parseDateTime(dt)
			DateTimeUtils.defaultFormatter.print(parsed)
		}catch{
			case err: IllegalArgumentException =>
				throw new UploadUserErrorException(err.getMessage)
		}

	private[this] val shaPattern = """[0-9a-fA-F]{64}""".r.pattern

	def ensureSha256(sum: String): String = {
		if(shaPattern.matcher(sum).matches) sum.toLowerCase
		else throw new UploadUserErrorException("Invalid SHA-256 sum, expecting a 32-byte hexadecimal string")
	}
}
