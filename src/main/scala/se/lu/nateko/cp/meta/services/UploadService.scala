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
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.InstanceServerUtils
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.utils.DateTimeUtils

class UploadService(server: InstanceServer, conf: UploadServiceConfig) {

	private val factory = server.factory
	private val vocab = new CpmetaVocab(factory)

	implicit def javaUriToSesame(uri: java.net.URI): URI = factory.createURI(uri)

	def registerUpload(meta: UploadMetadataDto, uploader: UserInfo): Try[String] = Try{
		import meta._

		val submitterConf = conf.submitters.get(submitterId).getOrElse(
			throw new UploadUserErrorException(s"Unknown submitter: $submitterId")
		)

		val userId = uploader.mail
		if(!submitterConf.authorizedUserIds.contains(userId))
			throw new UnauthorizedUploadException(s"User '$userId' is not authorized to upload on behalf of submitter '$submitterId'")

		val dataStrClass = InstanceServerUtils.getSingleType(dataStructure, server).toJava
		val datasetClass = submitterConf.structureToDatasetClassLookup.get(dataStrClass).getOrElse(
			throw new UploadUserErrorException(s"Submitter '$submitterId' cannot upload data structures of class $dataStrClass")
		)

		val datasetUri = factory.createURI(datasetClass, "/" + meta.hashSum)
		if(!server.getStatements(datasetUri).isEmpty)
			throw new UploadUserErrorException(s"Upload with hash sum ${meta.hashSum} has already been registered. Amendments are not supported yet!")

		if(server.getStatements(Some(producingOrganization), Some(RDF.TYPE), Some(submitterConf.producingOrganizationClass)).isEmpty)
			throw new UploadUserErrorException(s"Unknown producing organization: $producingOrganization")

		val submissionUri = factory.createURI(vocab.submissionClass, "/" + hashSum)
		val acquisitionUri = factory.createURI(vocab.acquisitionClass, "/" + hashSum)

		server.addAll(Seq[(URI, URI, Value)](

			(datasetUri, RDF.TYPE, datasetClass),
			(datasetUri, vocab.hasSha256sum, makeSha256Literal(hashSum)),
			(datasetUri, vocab.qb.structure, dataStructure),
			(datasetUri, vocab.wasAcquiredBy, acquisitionUri),
			(datasetUri, vocab.wasSubmittedBy, submissionUri),

			(acquisitionUri, RDF.TYPE, vocab.acquisitionClass),
			(acquisitionUri, vocab.prov.startedAtTime, makeDateTimeLiteral(meta.acquisitionStart)),
			(acquisitionUri, vocab.prov.endedAtTime, makeDateTimeLiteral(meta.acquisitionEnd)),
			(acquisitionUri, vocab.prov.wasAssociatedWith, producingOrganization),

			(submissionUri, RDF.TYPE, vocab.submissionClass),
			(submissionUri, vocab.prov.startedAtTime, factory.getDateTimeNow),
			(submissionUri, vocab.prov.wasAssociatedWith, submitterConf.submittingOrganization)

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
