package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.MalformedRequestContentRejection
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.Keep
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.DocObject
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.core.etcupload.EtcUploadMetadata
import se.lu.nateko.cp.meta.core.etcupload.JsonSupport.given
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.metaflow.MetaUploadService
import se.lu.nateko.cp.meta.metaflow.icos.AtcMetaSource
import se.lu.nateko.cp.meta.services.*
import se.lu.nateko.cp.meta.services.upload.*

import java.net.URI
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try
import se.lu.nateko.cp.meta.core.data.EnvriConfigs

object UploadApiRoute extends CpmetaJsonProtocol{

	private val errHandler = ExceptionHandler{
		case authErr: UnauthorizedUploadException =>
			complete((StatusCodes.Unauthorized, authErr.getMessage))
		case userErr: UploadUserErrorException =>
			complete((StatusCodes.BadRequest, userErr.getMessage))
		case err => throw err
	}

	private val replyWithErrorOnBadContent = handleRejections(
		RejectionHandler.newBuilder().handleAll[MalformedRequestContentRejection]{ rejs =>
			val msgs = rejs match{
				case Seq(single) =>
					single.message
				case _ =>
					rejs.zipWithIndex.map{
						case (rej, i) => s"Alternative ${i+1}: ${rej.message}"
					}
					.mkString("Attempts to parse the request resulted in the following errors:\n", "\n", "")
			}
			complete(StatusCodes.BadRequest -> msgs)
		}.result()
	)

	val Sha256Segment = Segment.flatMap(Sha256Sum.fromString(_).toOption)
	import AuthenticationRouting.ensureLocalRequest

	def apply(
		service: UploadService,
		authRouting: AuthenticationRouting,
		metaFlows: Seq[MetaUploadService],
		coreConf: MetaCoreConfig
	)(using Materializer): Route = handleExceptions(errHandler):

		given EnvriConfigs = coreConf.envriConfigs
		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		pathPrefix("upload"){
			pathPrefix(Segment){uplServiceId =>
				metaFlows.find(_.dirName == uplServiceId).fold(reject): uplService =>
					(post & path(Segment)): tblKind =>
						authRouting.mustBeLoggedIn: uploader =>
							onSuccess(Future.fromTry(uplService.getTableSink(tblKind, uploader))): sink =>
								extractDataBytes: data =>
									onSuccess(data.toMat(sink)(Keep.right).run()):_ =>
										complete(StatusCodes.OK)
					~
					getFromBrowseableDirectory(uplService.directory.toString)
			} ~
			post{
				path("etc"){
					(ensureLocalRequest & replyWithErrorOnBadContent){
						entity(as[EtcUploadMetadata]){ uploadMeta =>
							reportAccessUri(service.registerEtcUpload(uploadMeta))
						}
					}
				} ~
				extractEnvri{implicit envri =>
					path(Sha256Segment){hash =>
						(ensureLocalRequest & replyWithErrorOnBadContent){
							entity(as[UploadCompletionInfo]){ completionInfo =>
								onSuccess(service.completeUpload(hash, completionInfo)){r => complete(r.message)}
							}
						}
					} ~
					pathEnd{
						authRouting.mustBeLoggedIn{uploader =>
							replyWithErrorOnBadContent{
								entity(as[ObjectUploadDto]){uploadMeta =>
									reportAccessUri(service.registerUpload(uploadMeta, uploader))
								} ~
								entity(as[StaticCollectionDto]){collMeta =>
									reportAccessUri(service.registerStaticCollection(collMeta, uploader))
								}
							}
						}
					}
				}
			} ~
			get{
				import spray.json.*
				extractEnvri { implicit envri =>
					import MetaCoreConfig.given
					path("envri"){
						complete(envri)
					} ~
					path("envriconfig"){
						complete(coreConf.envriConfigs(envri))
					} ~
					path("permissions"){
						parameters("submitter", "userId")((submitter, userId) => {
							val isAllowed: Boolean = service.checkPermissions(new java.net.URI(submitter), userId)
							complete(JsBoolean(isAllowed))
						})
					} ~
					path("submitterids"){
						authRouting.mustBeLoggedIn { uploader =>
							complete(service.availableSubmitterIds(uploader))
						}
					}
				}
			}
		}
	end apply

	def reportAccessUri(uriTry: Try[AccessUri]): Route = complete(uriTry.get.uri.toString)
}
