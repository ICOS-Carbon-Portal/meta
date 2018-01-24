package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import scala.concurrent.Future
import scala.util.Try

import akka.actor.ActorSystem
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.UploadServiceConfig
import se.lu.nateko.cp.meta.api.EpicPidClient
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.core.etcupload.EtcUploadMetadata
import se.lu.nateko.cp.meta.services.upload.completion.UploadCompleter
import se.lu.nateko.cp.meta.services.upload.etc.EtcUploadTransformer
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.services.UploadUserErrorException

class UploadService(
		servers: DataObjectInstanceServers,
		sparql: SparqlRunner,
		etcHelper: EtcUploadTransformer,
		conf: UploadServiceConfig
)(implicit system: ActorSystem) {

	import servers.{ metaVocab, vocab }
	import system.dispatcher

	private implicit val factory = vocab.factory
	private val validator = new UploadValidator(servers, conf)
	private val epic = new EpicPidClient(conf.epicPid)
	private val completer = new UploadCompleter(servers, epic)
	private val metaUpdater = new DobjMetadataUpdater(vocab, metaVocab, sparql)
	private val staticCollUpdater = new StaticCollMetadataUpdater(vocab, metaVocab)
	private val statementProd = new StatementsProducer(vocab, metaVocab)

	def fetchDataObj(hash: Sha256Sum): Option[DataObject] = {
		val server = servers.getInstServerForDataObj(hash).get
		val objectFetcher = new DataObjectFetcher(server, vocab, metaVocab, epic.getPid)
		objectFetcher.fetch(hash)
	}

	def fetchStaticColl(hash: Sha256Sum)(implicit envri: Envri): Option[StaticCollection] = {
		servers.collectionServers.get(envri).flatMap{collServer =>
			val collFetcher = new CollectionFetcher(collServer, servers.allDataObjs, vocab, metaVocab)
			collFetcher.fetchStatic(hash)
		}
	}

	def registerUpload(meta: UploadMetadataDto, uploader: UserId): Future[String] = {
		val submitterOrgUriTry = for(
			_ <- validator.validateUpload(meta, uploader);
			submitterConf <- validator.getSubmitterConfig(meta.submitterId)
		) yield submitterConf.submittingOrganization

		for(
			submitterOrg <- Future.fromTry(submitterOrgUriTry);
			res <- registerUpload(meta, submitterOrg)
		) yield res
	}

	def registerEtcUpload(etcMeta: EtcUploadMetadata): Future[String] = {
		for(
			meta <- Future.fromTry(etcHelper.transform(etcMeta, vocab));
			response <- registerUpload(meta, vocab.getEcosystemStation(etcMeta.station).toJava)
		) yield response
	}

	def registerStaticCollection(coll: StaticCollectionDto, uploader: UserId)(implicit envri: Envri): Future[String] = {
		val collHashTry = Try{
			val sha256 = java.security.MessageDigest.getInstance("SHA-256")
			coll.members
				.map(_.toString.split('/').last)
				.sorted
				.foreach{segm =>
					val hash = Sha256Sum.fromBase64Url(segm).getOrElse{
						throw new UploadUserErrorException(
							"Static collection's members must be also static and therefore their URLs " +
							"must end with base64Url-encoded SHA-256 hashsums (full or truncated)"
						)
					}
					sha256.update(hash.truncate.getBytes.toArray)
				}
			new Sha256Sum(sha256.digest())
		}

		val resTry = for(
			collHash <- collHashTry;
			server <- Try{servers.collectionServers(envri)};
			_ <- validator.validateCollection(coll, collHash, uploader);
			submitterConf <- validator.getSubmitterConfig(coll.submitterId);
			submittingOrg = submitterConf.submittingOrganization;
			collIri = vocab.getCollection(collHash);
			newStatements = statementProd.getCollStatements(coll, collIri, submittingOrg);
			oldStatements = server.getStatements(collIri);
			updates = staticCollUpdater.calculateUpdates(collHash, oldStatements, newStatements);
			_ <- server.applyAll(updates)
		) yield collIri.toString

		Future.fromTry(resTry)
	}

	private def registerUpload(meta: UploadMetadataDto, submittingOrg: URI): Future[String] = {
		val serverTry = for(
			format <- servers.getObjSpecificationFormat(meta.objectSpecification.toRdf);
			server <- servers.getInstServerForFormat(format)
		) yield server

		for(
			server <- Future.fromTry(serverTry);
			newStatements = statementProd.getStatements(meta, submittingOrg);
			currentStatements <- metaUpdater.getCurrentStatements(meta.hashSum, server);
			updates = metaUpdater.calculateUpdates(meta.hashSum, currentStatements, newStatements);
			_ <- Future.fromTry(server.applyAll(updates))
		) yield{
			vocab.getDataObjectAccessUrl(meta.hashSum).toString
		}
	}

	def checkPermissions(submitter: URI, userId: String): Boolean =
		conf.submitters.values
			.filter(_.submittingOrganization === submitter)
			.exists(_.authorizedUserIds.contains(userId))

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo): Future[String] =
		completer.completeUpload(hash, info).map(_.message)

}
