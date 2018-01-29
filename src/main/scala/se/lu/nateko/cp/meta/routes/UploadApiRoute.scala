package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.MalformedRequestContentRejection
import akka.http.scaladsl.server.RejectionHandler

import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.JsonSupport._
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.services._
import se.lu.nateko.cp.meta.services.upload._
import se.lu.nateko.cp.meta.core.etcupload.EtcUploadMetadata
import se.lu.nateko.cp.meta.core.etcupload.JsonSupport._
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.MetaCoreConfig.EnvriConfigs
import se.lu.nateko.cp.meta.StaticCollectionDto

object UploadApiRoute extends CpmetaJsonProtocol{

	private val errHandler = ExceptionHandler{
		case authErr: UnauthorizedUploadException =>
			complete((StatusCodes.Unauthorized, authErr.getMessage))
		case userErr: UploadUserErrorException =>
			complete((StatusCodes.BadRequest, userErr.getMessage))
		case err => throw err
	}
	private val replyWithErrorOnBadContent = handleRejections(
		RejectionHandler.newBuilder().handle{
			case MalformedRequestContentRejection(msg, _) =>
				complete((StatusCodes.BadRequest, msg))
		}.result()
	)

	val Sha256Segment = Segment.flatMap(Sha256Sum.fromString(_).toOption)
	import AuthenticationRouting.ensureLocalRequest

	def apply(
		service: UploadService,
		authRouting: AuthenticationRouting,
		coreConf: MetaCoreConfig
	)(implicit configs: EnvriConfigs): Route = handleExceptions(errHandler){

		val pcm = new PageContentMarshalling(coreConf.handleService)
		import pcm.{dataObjectMarshaller, statCollMarshaller}

		pathPrefix("upload"){
			post{
				path("etc"){
					(ensureLocalRequest & replyWithErrorOnBadContent){
						entity(as[EtcUploadMetadata]){ uploadMeta =>
							complete(service.registerEtcUpload(uploadMeta))
						}
					}
				} ~
				extractHost{hostname =>
					implicit val envri = CpVocab.inferEnvri(hostname)
					path(Sha256Segment){hash =>
						(ensureLocalRequest & replyWithErrorOnBadContent){
							entity(as[UploadCompletionInfo]){ completionInfo =>
								onSuccess(service.completeUpload(hash, completionInfo)){complete(_)}
							}
						}
					} ~
					pathEnd{
						authRouting.mustBeLoggedIn{uploader =>
							replyWithErrorOnBadContent{
								entity(as[UploadMetadataDto]){uploadMeta =>
									complete(service.registerUpload(uploadMeta, uploader))
								} ~
								entity(as[StaticCollectionDto]){collMeta =>
									complete(service.registerStaticCollection(collMeta, uploader))
								}
							}
						}
					}
				}
			} ~
			get{
				path("permissions"){
					parameters(('submitter, 'userId))((submitter, userId) => {
						val isAllowed: Boolean = service.checkPermissions(new java.net.URI(submitter), userId)
						complete(spray.json.JsBoolean(isAllowed))
					})
				}
			}
		} ~
		get{
			extractHost{hostname =>
				implicit val envri = CpVocab.inferEnvri(hostname)
				path("objects" / Sha256Segment){ hash =>
					complete(() => service.fetchDataObj(hash))
				} ~
				path("collections" / Sha256Segment){ hash =>
					complete(() => service.fetchStaticColl(hash))
				}
			}
		}
	}
}