package se.lu.nateko.cp.meta.services.upload

import java.time.Instant

import scala.concurrent.Future
import scala.util.Try

import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.vocabulary.XMLSchema

import akka.actor.ActorSystem
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.api.EpicPidClient
import se.lu.nateko.cp.meta.api.PidUpdate
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.utils.sesame._
import spray.json.JsString

class UploadService(
	server: InstanceServer,
	conf: UploadServiceConfig
)(implicit system: ActorSystem) {
	import system.dispatcher

	private implicit val factory = server.factory
	private val vocab = new CpmetaVocab(factory)
	private val epic = EpicPidClient(conf.epicPid)

	val packageFetcher = new DataObjectFetcher(server, getPid)

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
		
		val triplesToAdd = Seq[(URI, URI, Value)](

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
		) ++ optionals

		server.addAll(triplesToAdd.map(factory.tripleToStatement))
			.map(_ => vocab.getDataObjectAccessUrl(hashSum, None).stringValue)
			.get
	}

	def checkPermissions(submitter: java.net.URI, userId: String): Boolean =
		conf.submitters.values
			.filter(_.submittingOrganization == submitter)
			.exists(_.authorizedUserIds.contains(userId))

	def completeUpload(hash: Sha256Sum): Future[String] = {
		val submissionUri = vocab.getSubmission(hash)

		if(server.getValues(submissionUri, vocab.prov.endedAtTime).isEmpty){
			val targetUri = vocab.getDataObject(hash)
			val pidEntry = PidUpdate("URL", JsString(targetUri.toString))

			epic.create(getPidSuffix(hash), Seq(pidEntry)).map(_ => {
				server.add(factory.createStatement(submissionUri, vocab.prov.endedAtTime, vocab.lit(Instant.now)))
				getPid(hash)
			})
		} else
			Future.failed(new Exception(s"Upload of $hash is already complete"))
	}

	def getPidSuffix(hash: Sha256Sum) = hash.id
	def getPid(hash: Sha256Sum) = epic.getPid(getPidSuffix(hash))

}
