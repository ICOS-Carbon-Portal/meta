package se.lu.nateko.cp.meta.services.upload

import java.net.URI

import scala.concurrent.Future
import scala.util.Try

import akka.actor.ActorSystem
import akka.stream.Materializer
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.{ ObjectUploadDto, StaticCollectionDto, SubmitterProfile, UploadServiceConfig }
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.core.etcupload.EtcUploadMetadata
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.services.upload.completion.UploadCompleter
import se.lu.nateko.cp.meta.services.upload.etc.EtcUploadTransformer
import se.lu.nateko.cp.meta.services.upload.validation.UploadValidator
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.services.upload.completion.Report
import se.lu.nateko.cp.meta.ConfigLoader
import org.eclipse.rdf4j.model.ValueFactory
import eu.icoscp.envri.Envri

class AccessUri(val uri: URI)

class UploadService(
	val servers: DataObjectInstanceServers,
	sparql: SparqlRunner,
	val etcHelper: EtcUploadTransformer,
	conf: UploadServiceConfig
)(using system: ActorSystem, mat: Materializer) {

	import servers.{ metaVocab, vocab }
	import system.{ dispatcher, log }

	private[this] val objLock = new ObjectLock("is currently already being uploaded")

	private given ValueFactory = vocab.factory
	private val validator = new UploadValidator(servers)
	private val handles = new HandleNetClient(conf.handle)
	private val completer = new UploadCompleter(servers, handles)
	private val metaUpdater = new ObjMetadataUpdater(vocab, metaVocab, sparql)
	private val staticCollUpdater = new StaticCollMetadataUpdater(vocab, metaVocab)
	private def statementProd(server: InstanceServer) = new StatementsProducer(server, vocab, metaVocab)

	def registerUpload(meta0: ObjectUploadDto, uploader: UserId)(using Envri): Future[AccessUri] = {
		val metaAndSubmitterTry = for(
			meta <- validator.validateObject(meta0, uploader);
			submitterConf <- validator.getSubmitterConfig(meta)
		) yield meta -> submitterConf.submittingOrganization

		for(
			(meta, submitterOrg) <- Future.fromTry(metaAndSubmitterTry);
			_ <- Future.fromTry(objLock.lock(meta0.hashSum));
			accessUri <- meta match {
				case dobj: DataObjectDto =>
					registerDataObjUpload(dobj, submitterOrg)
				case _: DocObjectDto =>
					registerObjUpload(meta, servers.getDocInstServer, submitterOrg)
			}
		) yield
			objLock.unlock(meta0.hashSum)
			accessUri
	}

	def registerEtcUpload(etcMeta: EtcUploadMetadata): Future[AccessUri] = {
		implicit val envri = Envri.ICOS
		for(
			meta <- Future.fromTry(etcHelper.transform(etcMeta));
			accessUri <- registerDataObjUpload(meta, vocab.getEcosystemStation(etcMeta.station).toJava)
		) yield accessUri
	}

	def registerStaticCollection(coll: StaticCollectionDto, uploader: UserId)(implicit envri: Envri): Future[AccessUri] = {
		val resTry = for(
			collHash <- UploadService.collectionHash(coll.members);
			server <- Try{servers.collectionServers(envri)};
			_ <- validator.validateCollection(coll, collHash, uploader);
			submitterConf <- validator.getSubmitterConfig(coll);
			submittingOrg = submitterConf.submittingOrganization;
			collIri = vocab.getCollection(collHash);
			newStatements = statementProd(server).getCollStatements(coll, collIri, submittingOrg);
			oldStatements = server.getStatements(collIri);
			updates = staticCollUpdater.calculateUpdates(collHash, oldStatements, newStatements, server);
			_ <- server.applyAll(updates)()
		) yield new AccessUri(collIri.toJava)

		Future.fromTry(resTry)
	}

	private def registerDataObjUpload(meta: DataObjectDto, submittingOrg: URI)(implicit envri: Envri): Future[AccessUri] = {
		val serverTry = for(
			format <- servers.getObjSpecificationFormat(meta.objectSpecification.toRdf);
			server <- servers.getInstServerForFormat(format)
		) yield server

		registerObjUpload(meta, serverTry, submittingOrg)
	}

	private def registerObjUpload(dto: ObjectUploadDto, serverTry: Try[InstanceServer], submittingOrg: URI)(using Envri): Future[AccessUri] =
		for
			server <- Future.fromTry(serverTry);
			_ <- Future.fromTry(validator.updateValidIfObjectNotNew(dto, submittingOrg));
			newStatements = statementProd(server).getObjStatements(dto, submittingOrg);
			currentStatements <- metaUpdater.getCurrentStatements(dto.hashSum, server);
			updates = metaUpdater.calculateUpdates(dto.hashSum, currentStatements, newStatements, server);
			_ = log.debug(s"Computed ${updates.size} RDF updates for metadata upload for object ${dto.hashSum.id}, will apply them now...");
			_ <- Future.fromTry(server.applyAll(updates)())
		yield
			log.debug(s"Updates for object ${dto.hashSum.id} have been applied successfully")
			new AccessUri(vocab.getStaticObjectAccessUrl(dto.hashSum))

	def checkPermissions(submitter: URI, userId: String)(implicit envri: Envri): Boolean =
		ConfigLoader.submittersConfig.submitters(envri).values
			.filter(_.submittingOrganization === submitter)
			.exists(_.authorizedUserIds.contains(userId))

	def availableSubmitterIds(uploader: UserId)(implicit envri: Envri): Seq[SubmitterProfile] = ConfigLoader.submittersConfig.submitters(envri).collect{
		case (id, submConf) if submConf.authorizedUserIds.contains(uploader.email) =>
			SubmitterProfile(id, submConf.producingOrganizationClass, submConf.producingOrganization, submConf.authorizedThemes, submConf.authorizedProjects)
	}.toSeq.sortBy(sp => sp.id)

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo)(implicit envri: Envri): Future[Report] =
		completer.completeUpload(hash, info)

}

object UploadService{

	def collectionHash(items: Seq[URI]): Try[Sha256Sum] = Try{
		val sha256 = java.security.MessageDigest.getInstance("SHA-256")
		items
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
}
