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
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer.Hash
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.services.upload.completion.UploadCompleter
import se.lu.nateko.cp.meta.services.upload.etc.EtcUploadTransformer
import se.lu.nateko.cp.meta.services.upload.validation.UploadValidator
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.services.upload.completion.Report
import se.lu.nateko.cp.meta.ConfigLoader
import org.eclipse.rdf4j.model.ValueFactory
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.services.upload.DoiGeoLocationConverter.fromJtsToGeoFeature
import akka.http.scaladsl.model.Uri
import se.lu.nateko.cp.meta.utils.Validated
import scala.util.Success

class AccessUri(val uri: URI)

class UploadService(
	val servers: DataObjectInstanceServers,
	val etcHelper: EtcUploadTransformer,
	conf: UploadServiceConfig
)(using system: ActorSystem, mat: Materializer):

	import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*
	import RdfLens.GlobConn
	import servers.{ metaVocab, vocab, metaReader }
	import system.{ dispatcher, log }

	private val uploadLock = new UploadLock

	private given vf: ValueFactory = vocab.factory
	private val validator = new UploadValidator(servers)
	private val handles = new HandleNetClient(conf.handle)
	private val completer = new UploadCompleter(servers, handles)
	private val metaUpdater = new ObjMetadataUpdater(vocab, metaVocab)
	private val staticCollUpdater = new StaticCollMetadataUpdater(vocab, metaVocab)
	private val statementProd = StatementsProducer(vocab, metaVocab)

	def registerUpload(meta0: ObjectUploadDto, uploader: UserId)(using Envri): Try[AccessUri] =
		for
			meta <- validator.validateObject(meta0, uploader)
			submitterConf <- validator.getSubmitterConfig(meta)
			submitterOrg = submitterConf.submittingOrganization
			accessUri <- meta match
				case dobj: DataObjectDto =>
					registerDataObjUpload(dobj, submitterOrg)
				case _: DocObjectDto =>
					servers.docServer.toTry(new MetadataException(_)).flatMap(registerObjUpload(meta, _, submitterOrg))
		yield accessUri


	def registerEtcUpload(etcMeta: EtcUploadMetadata): Try[AccessUri] =
		given Envri = Envri.ICOS
		for
			meta <- etcHelper.transform(etcMeta)
			accessUri <- registerDataObjUpload(meta, vocab.getEcosystemStation(etcMeta.station).toJava)
		yield accessUri

	def registerStaticCollection(coll: StaticCollectionDto, uploader: UserId)(using Envri): Try[AccessUri] =

		UploadService.collectionHash(coll.members).flatMap: collHash =>
			uploadLock.wrapTry(collHash):
				servers.vanillaGlobal.access: conn ?=>
					given GlobConn = RdfLens.global(using conn)
					val collWithCoverageTry = coll.coverage match
						case Some(_) => Success(coll)
						case None =>
							getCollCoverages(coll.members).toTry(new MetadataException(_)).map: covs =>
								val gfs = geocov.GeoCovMerger.representativeCoverage(covs, 100).flatMap(fromJtsToGeoFeature)
								val cov = gfs.toList match
									case Nil => None
									case feature :: Nil => Some(Left(feature))
									case many => Some(Left(FeatureCollection(many, None, None)))
								coll.copy(coverage = cov)
					for
						collWithCoverage <- collWithCoverageTry;
						_ <- validator.validateCollection(collWithCoverage, collHash, uploader);
						submitterConf <- validator.getSubmitterConfig(collWithCoverage);
						submittingOrg = submitterConf.submittingOrganization;
						collIri = vocab.getCollection(collHash);
						server <- servers.collectionServer.toTry(new MetadataException(_))
						updates = server.access:
							val newStatements = statementProd.getCollStatements(collWithCoverage, collIri, submittingOrg)
							val oldStatements = getStatements(collIri)
							staticCollUpdater.calculateUpdates(collHash, oldStatements, newStatements)
						_ <- server.applyAll(updates)()
					yield
						AccessUri(collIri.toJava)


	private def registerDataObjUpload(meta: DataObjectDto, submittingOrg: URI)(using Envri): Try[AccessUri] =
		val serverV = servers.vanillaGlobal.access:
			for
				metaLens <- servers.lenses.metaInstanceLens
				format <- metaReader.getObjSpecFormat(meta.objectSpecification.toRdf)(using metaLens)
				server <- servers.getInstServerForFormat(format)
			yield server
		for
			server <- serverV.toTry(new MetadataException(_))
			accessUri <- registerObjUpload(meta, server, submittingOrg)
		yield accessUri


	private def registerObjUpload(dto: ObjectUploadDto, server: InstanceServer, submittingOrg: URI)(using Envri): Try[AccessUri] =
		uploadLock.wrapTry(dto.hashSum):
			server.access: conn ?=>
				given GlobConn = RdfLens.global(using conn)
				for
					_ <- validator.updateValidIfObjectNotNew(dto, submittingOrg)
					updates =
						val newStatements = statementProd.getObjStatements(dto, submittingOrg)
						val currentStatements = metaUpdater.getCurrentStatements(dto.hashSum)
						metaUpdater.calculateUpdates(dto.hashSum, currentStatements, newStatements)
					_ = log.debug(s"Computed ${updates.size} RDF updates for metadata upload for object ${dto.hashSum.id}, will apply them now...");
					_ <- server.applyAll(updates)()
				yield
					log.debug(s"Updates for object ${dto.hashSum.id} have been applied successfully")
					new AccessUri(vocab.getStaticObjectAccessUrl(dto.hashSum))

	private def getCollCoverages(members: Seq[URI])(using Envri, GlobConn): Validated[Seq[GeoFeature]] = Validated
		.sequence:
			members.map: uri =>
				Uri(uri.toString).path match
					case Hash.Object(_) =>
						metaReader.fetchStaticObject(uri.toRdf).map:
							case dobj: DataObject => dobj.coverage
							case docObj: DocObject => None

					case Hash.Collection(collHash) =>
						metaReader.fetchStaticColl(uri.toRdf, Some(collHash)).map(_.coverage)
					case _ => Validated.ok(None)
		.map(_.flatten)


	def checkPermissions(submitter: URI, userId: String)(using envri: Envri): Boolean =
		ConfigLoader.submittersConfig.submitters(envri).values
			.filter(_.submittingOrganization === submitter)
			.exists(_.authorizedUserIds.contains(userId))

	def availableSubmitterIds(uploader: UserId)(using envri: Envri): Seq[SubmitterProfile] = ConfigLoader.submittersConfig.submitters(envri).collect{
		case (id, submConf) if submConf.authorizedUserIds.contains(uploader.email) =>
			SubmitterProfile(id, submConf.producingOrganizationClass, submConf.producingOrganization, submConf.authorizedThemes, submConf.authorizedProjects)
	}.toSeq.sortBy(sp => sp.id)

	def completeUpload(hash: Sha256Sum, info: UploadCompletionInfo)(using Envri): Future[Report] =
		uploadLock.wrapFuture(hash):
			completer.completeUpload(hash, info)

end UploadService

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
