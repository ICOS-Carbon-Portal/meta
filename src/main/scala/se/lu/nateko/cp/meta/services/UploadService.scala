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

		val packageSpecClass = InstanceServerUtils.getSingleType(packageSpec, server)
		val dataLevel = server.getValues(packageSpec, vocab.hasDataLevel)
			.collect{case lit: Literal => lit.intValue}
			.head

		val packageUri = factory.createURI(vocab.dataPackageClass, "/" + meta.hashSum)
		if(!server.getStatements(packageUri).isEmpty)
			throw new UploadUserErrorException(s"Upload with hash sum ${meta.hashSum} has already been registered. Amendments are not supported yet!")

		if(server.getStatements(Some(producingOrganization), Some(RDF.TYPE), Some(submitterConf.producingOrganizationClass)).isEmpty)
			throw new UploadUserErrorException(s"Unknown producing organization: $producingOrganization")

		val submissionUri = factory.createURI(vocab.submissionClass, "/" + hashSum)
		val provActivityClass = getProvActivityClass(packageSpecClass, dataLevel)
		val provActivityUri = factory.createURI(provActivityClass, "/" + hashSum)

		server.addAll(Seq[(URI, URI, Value)](

			(packageUri, RDF.TYPE, vocab.dataPackageClass),
			(packageUri, vocab.hasSha256sum, makeSha256Literal(hashSum)),
			(packageUri, vocab.hasPackageSpec, packageSpec),
			(packageUri, vocab.wasAcquiredBy, provActivityUri), //TODO Generalize! This is only valid for L0 packages.
			(packageUri, vocab.wasSubmittedBy, submissionUri),

			(provActivityUri, RDF.TYPE, provActivityClass),
			(provActivityUri, vocab.prov.wasAssociatedWith, producingOrganization),

			(submissionUri, RDF.TYPE, vocab.submissionClass),
			(submissionUri, vocab.prov.startedAtTime, factory.getDateTimeNow),
			(submissionUri, vocab.prov.wasAssociatedWith, submitterConf.submittingOrganization)

		).map(factory.tripleToStatement))

		packageUri.stringValue
	}

	import UploadService._

//	private def makeDateTimeLiteral(dt: String): Literal =
//		factory.createLiteral(dateTimeToUtc(dt), XMLSchema.DATETIME)

	private def makeSha256Literal(sum: String): Literal =
		factory.createLiteral(ensureSha256(sum), XMLSchema.HEXBINARY)

	private def getProvActivityClass(packageSpecClass: URI, dataLevel: Int): URI = {
		vocab.acquisitionClass
	}
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
