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
import scala.util.Failure

class UploadService(server: InstanceServer, conf: UploadServiceConfig) {

	private implicit val factory = server.factory
	private val vocab = new CpmetaVocab(factory)

	def registerUpload(meta: UploadMetadataDto, uploader: UserInfo): Try[String] = Try{
		import meta._

		val submitterConf = conf.submitters.get(submitterId).getOrElse(
			throw new UploadUserErrorException(s"Unknown submitter: $submitterId")
		)

		val userId = uploader.mail
		if(!submitterConf.authorizedUserIds.contains(userId))
			throw new UnauthorizedUploadException(s"User '$userId' is not authorized to upload on behalf of submitter '$submitterId'")

		val packageId = UploadService.getPackageId(hashSum)
		val packageUri = vocab.getFile(packageId)
		if(server.getStatements(packageUri).nonEmpty)
			throw new UploadUserErrorException(s"Upload with hash sum $hashSum has already been registered. Amendments are not supported yet!")

		if(server.getStatements(packageSpec).isEmpty)
			throw new UploadUserErrorException(s"Unknown package specification: $packageSpec")

		if(!server.hasStatement(producingOrganization, RDF.TYPE, submitterConf.producingOrganizationClass))
			throw new UploadUserErrorException(s"Unknown producing organization: $producingOrganization")

		val submissionUri = factory.createURI(vocab.submissionClass, "/" + packageId)
		val productionUri = factory.createURI(vocab.productionClass, "/" + packageId)

		server.addAll(Seq[(URI, URI, Value)](

			(packageUri, RDF.TYPE, vocab.dataPackageClass),
			(packageUri, vocab.hasSha256sum, vocab.lit(hashSum, XMLSchema.HEXBINARY)),
			(packageUri, vocab.hasPackageSpec, packageSpec),
			(packageUri, vocab.wasProducedBy, productionUri),
			(packageUri, vocab.wasSubmittedBy, submissionUri),

			(productionUri, RDF.TYPE, vocab.productionClass),
			(productionUri, vocab.prov.wasAssociatedWith, producingOrganization),

			(submissionUri, RDF.TYPE, vocab.submissionClass),
			(submissionUri, vocab.prov.startedAtTime, factory.getDateTimeNow),
			(submissionUri, vocab.prov.wasAssociatedWith, submitterConf.submittingOrganization)

		).map(factory.tripleToStatement))

		packageUri.stringValue
	}

}

object UploadService{
	import se.lu.nateko.cp.meta.utils.HashSumUtils._

	private def getOrBlameUser(maybe: Try[String]): String = maybe.recoverWith{
		case err => Failure(new UploadUserErrorException(err.getMessage))
	}.get

	def getPackageId(hex: String): String = getOrBlameUser(toUrlSafeSha256Base64(hex))

}
