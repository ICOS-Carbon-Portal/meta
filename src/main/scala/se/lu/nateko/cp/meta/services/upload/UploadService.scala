package se.lu.nateko.cp.meta.services.upload

import akka.actor.ActorSystem
import java.time.Instant
import scala.concurrent.Future
import scala.util.Try
import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.vocabulary.XMLSchema
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.DataSubmitterConfig
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
import org.openrdf.model.Statement

class UploadService(server: InstanceServer, conf: UploadServiceConfig)(implicit system: ActorSystem) {
	import system.dispatcher

	private implicit val factory = server.factory
	private val vocab = new CpmetaVocab(factory)
	private val validator = new UploadValidator(server, conf, vocab)
	private val completer = new UploadCompleter(server, conf.epicPid, vocab)

	val objectFetcher = new DataObjectFetcher(server, completer.getPid)

	def registerUpload(meta: UploadMetadataDto, uploader: UserInfo): Try[String] =
		for(
			_ <- validator.validateUpload(meta, uploader);
			submitterConf <- validator.getSubmitterConfig(meta);
			_ <- server.addAll(getStatements(meta, submitterConf))
		) yield{
			vocab.getDataObjectAccessUrl(meta.hashSum, meta.fileName).stringValue
		}

	def checkPermissions(submitter: java.net.URI, userId: String): Boolean =
		conf.submitters.values
			.filter(_.submittingOrganization == submitter)
			.exists(_.authorizedUserIds.contains(userId))

	def completeUpload(hash: Sha256Sum): Future[String] = completer.completeUpload(hash, EmptyCompletionInfo)


	private def getStatements(meta: UploadMetadataDto, submConf: DataSubmitterConfig): Seq[Statement] = {
		import meta.{hashSum, objectSpecification, producingOrganization, productionInterval}

		val objectUri = vocab.getDataObject(hashSum)
		val submissionUri = vocab.getSubmission(hashSum)
		val productionUri = vocab.getProduction(hashSum)

		val prodStart = productionInterval.map(_.start)
		val prodStop = productionInterval.map(_.stop)

		val optionals: Seq[(URI, URI, Value)] =
			Seq(
				(objectUri, vocab.hasName, meta.fileName.map(vocab.lit)),
				(productionUri, vocab.prov.startedAtTime, prodStart.map(vocab.lit)),
				(productionUri, vocab.prov.endedAtTime, prodStop.map(vocab.lit))
			).collect{
				case (s, p, Some(o)) => (s, p, o)
			}

		val mandatory = Seq[(URI, URI, Value)](
			(objectUri, RDF.TYPE, vocab.dataObjectClass),
			(objectUri, vocab.hasSha256sum, vocab.lit(hashSum.hex, XMLSchema.HEXBINARY)),
			(objectUri, vocab.hasObjectSpec, objectSpecification),
			(objectUri, vocab.wasProducedBy, productionUri),
			(objectUri, vocab.wasSubmittedBy, submissionUri),

			(productionUri, RDF.TYPE, vocab.productionClass),
			(productionUri, vocab.prov.wasAssociatedWith, producingOrganization),

			(submissionUri, RDF.TYPE, vocab.submissionClass),
			(submissionUri, vocab.prov.startedAtTime, vocab.lit(Instant.now)),
			(submissionUri, vocab.prov.wasAssociatedWith, submConf.submittingOrganization)
		)

		(mandatory ++ optionals).map(factory.tripleToStatement)
	}
}
