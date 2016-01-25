package se.lu.nateko.cp.meta.services.upload

import scala.util.Try
import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.vocabulary.XMLSchema
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import java.time.Instant

class UploadService(server: InstanceServer, conf: UploadServiceConfig) {

	private implicit val factory = server.factory
	private val vocab = new CpmetaVocab(factory)

	val packageFetcher = new DataObjectFetcher(server)

	def registerUpload(meta: UploadMetadataDto, uploader: UserInfo): Try[String] = Try{
		import meta.{hashSum, submitterId, packageSpec, producingOrganization}

		val submitterConf = conf.submitters.get(submitterId).getOrElse(
			throw new UploadUserErrorException(s"Unknown submitter: $submitterId")
		)

		val userId = uploader.mail
		if(!submitterConf.authorizedUserIds.contains(userId))
			throw new UnauthorizedUploadException(s"User '$userId' is not authorized to upload on behalf of submitter '$submitterId'")

		val packageUri = vocab.getDataObject(hashSum)
		if(server.getStatements(packageUri).nonEmpty)
			throw new UploadUserErrorException(s"Upload with hash sum $hashSum has already been registered. Amendments are not supported yet!")

		if(server.getStatements(packageSpec).isEmpty)
			throw new UploadUserErrorException(s"Unknown package specification: $packageSpec")

		if(!server.hasStatement(producingOrganization, RDF.TYPE, submitterConf.producingOrganizationClass))
			throw new UploadUserErrorException(s"Unknown producing organization: $producingOrganization")

		val submissionUri = vocab.getSubmission(hashSum)
		val productionUri = vocab.getProduction(hashSum)

		val optionals: Seq[(URI, URI, Value)] = Seq(
			(packageUri, vocab.hasName, meta.fileName.map(vocab.lit))
		).map{case (s, p, oOpt) => oOpt.map((s, p, _))}.flatten
		
		server.addAll(Seq[(URI, URI, Value)](

			(packageUri, RDF.TYPE, vocab.dataObjectClass),
			(packageUri, vocab.hasSha256sum, vocab.lit(hashSum.hex, XMLSchema.HEXBINARY)),
			(packageUri, vocab.hasPackageSpec, packageSpec),
			(packageUri, vocab.wasProducedBy, productionUri),
			(packageUri, vocab.wasSubmittedBy, submissionUri),

			(productionUri, RDF.TYPE, vocab.productionClass),
			(productionUri, vocab.prov.wasAssociatedWith, producingOrganization),
			(productionUri, vocab.prov.startedAtTime, vocab.lit(meta.productionStart)),
			(productionUri, vocab.prov.endedAtTime, vocab.lit(meta.productionEnd)),

			(submissionUri, RDF.TYPE, vocab.submissionClass),
			(submissionUri, vocab.prov.startedAtTime, vocab.lit(Instant.now)),
			(submissionUri, vocab.prov.wasAssociatedWith, submitterConf.submittingOrganization)
		).++(optionals).map(factory.tripleToStatement))

		packageUri.stringValue
	}

	def checkPermissions(submitter: java.net.URI, userId: String): Boolean =
		conf.submitters.values
			.filter(_.submittingOrganization == submitter)
			.exists(_.authorizedUserIds.contains(userId))

}
