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
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.utils.sesame._
import spray.json.JsString
import org.openrdf.model.Statement
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.ElaboratedProductMetadata
import se.lu.nateko.cp.meta.StationDataMetadata
import se.lu.nateko.cp.meta.DataProductionDto

class UploadService(servers: DataObjectInstanceServers, conf: UploadServiceConfig)(implicit system: ActorSystem) {
	import system.dispatcher

	private implicit val factory = servers.icosMeta.factory
	import servers.{vocab, metaVocab}
	private val validator = new UploadValidator(servers, conf)
	private val completer = new UploadCompleter(servers, conf)

	def fetchDataObj(hash: Sha256Sum): Option[DataObject] = {
		val server = servers.getInstServerForDataObj(hash).get
		val objectFetcher = new DataObjectFetcher(server, vocab, metaVocab, completer.getPid)
		objectFetcher.fetch(hash)
	}

	def registerUpload(meta: UploadMetadataDto, uploader: UserInfo): Try[String] =
		for(
			_ <- validator.validateUpload(meta, uploader);
			submitterConf <- validator.getSubmitterConfig(meta);
			format <- servers.getObjSpecificationFormat(meta.objectSpecification);
			server <- servers.getInstServerForFormat(format);
			_ <- server.addAll(getStatements(meta, submitterConf))
		) yield{
			vocab.getDataObjectAccessUrl(meta.hashSum, meta.fileName).stringValue
		}

	def checkPermissions(submitter: java.net.URI, userId: String): Boolean =
		conf.submitters.values
			.filter(_.submittingOrganization == submitter)
			.exists(_.authorizedUserIds.contains(userId))

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo): Future[String] =
		completer.completeUpload(hash, info)


	private def getStatements(meta: UploadMetadataDto, submConf: DataSubmitterConfig): Seq[Statement] = {
		import meta.{hashSum, objectSpecification}

		val objectUri = vocab.getDataObject(hashSum)
		val submissionUri = vocab.getSubmission(hashSum)

		val specificStatements = meta.specificInfo.fold(
			elProd => getElaboratedProductStatements(hashSum, elProd),
			stationData => getStationDataStatements(hashSum, stationData)
		)

		specificStatements ++
		makeSt(objectUri, metaVocab.hasName, meta.fileName.map(vocab.lit)) ++ Seq(
			makeSt(objectUri, RDF.TYPE, metaVocab.dataObjectClass),
			makeSt(objectUri, metaVocab.hasSha256sum, vocab.lit(hashSum.hex, XMLSchema.HEXBINARY)),
			makeSt(objectUri, metaVocab.hasObjectSpec, objectSpecification),
			makeSt(objectUri, metaVocab.wasSubmittedBy, submissionUri),
			makeSt(submissionUri, RDF.TYPE, metaVocab.submissionClass),
			makeSt(submissionUri, metaVocab.prov.startedAtTime, vocab.lit(Instant.now)),
			makeSt(submissionUri, metaVocab.prov.wasAssociatedWith, submConf.submittingOrganization)
		)
	}

	private def getElaboratedProductStatements(hash: Sha256Sum, meta: ElaboratedProductMetadata): Seq[Statement] = {
		//TODO encode to RDF Spatial and Temporal coverage
		val objUri = vocab.getDataObject(hash)
		makeSt(objUri, metaVocab.dcterms.title, vocab.lit(meta.title)) +: (
			makeSt(objUri, metaVocab.dcterms.description, meta.description.map(vocab.lit)) ++
			getProductionStatements(hash, meta.production)
		)
	}

	private def getStationDataStatements(hash: Sha256Sum, meta: StationDataMetadata): Seq[Statement] = {
		val objectUri = vocab.getDataObject(hash)
		val aquisitionUri = vocab.getAcquisition(hash)
		val acqStart = meta.aquisitionInterval.map(_.start)
		val acqStop = meta.aquisitionInterval.map(_.stop)

		val productionStatements = meta.production.map(getProductionStatements(hash, _)).getOrElse(Seq.empty)
		Seq(
			makeSt(objectUri, metaVocab.wasAcquiredBy, aquisitionUri),
			makeSt(aquisitionUri, RDF.TYPE, metaVocab.aquisitionClass),
			makeSt(aquisitionUri, metaVocab.prov.wasAssociatedWith, meta.station)
		) ++
		makeSt(aquisitionUri, metaVocab.prov.startedAtTime, acqStart.map(vocab.lit)) ++
		makeSt(aquisitionUri, metaVocab.prov.endedAtTime, acqStop.map(vocab.lit)) ++
		productionStatements
	}

	private def getProductionStatements(hash: Sha256Sum, prod: DataProductionDto): Seq[Statement] = {
		val productionUri = vocab.getProduction(hash)
		val objectUri = vocab.getDataObject(hash)
		//makeSt(objectUri, metaVocab.wasProducedBy, productionUri),
		???
	}
	private def makeSt(subj: URI, pred: URI, obj: Option[Value]): Seq[Statement] = obj match {
		case None => Seq.empty
		case Some(o) => Seq(factory.createStatement(subj, pred, o))
	}

	private def makeSt(subj: URI, pred: URI, obj: Value): Statement = factory.createStatement(subj, pred, obj)

}
