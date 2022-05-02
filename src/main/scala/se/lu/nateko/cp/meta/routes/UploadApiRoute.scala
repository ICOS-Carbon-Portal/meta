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
import scala.concurrent.Future
import scala.collection.immutable.Seq
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.icos.AtcMetaSource
import se.lu.nateko.cp.meta.services.*
import se.lu.nateko.cp.meta.services.upload.*
import se.lu.nateko.cp.meta.core.etcupload.EtcUploadMetadata
import se.lu.nateko.cp.meta.core.etcupload.JsonSupport.given
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.StaticCollectionDto
import scala.language.implicitConversions

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
		atcMetaSource: AtcMetaSource,
		coreConf: MetaCoreConfig
	)(implicit mat: Materializer): Route = handleExceptions(errHandler){

		implicit val configs = coreConf.envriConfigs
		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		pathPrefix("upload"){
			pathPrefix("atcmeta"){
				post{
					path(Segment){tblKind =>
						authRouting.mustBeLoggedIn{uploader =>
							onSuccess(Future.fromTry(atcMetaSource.getTableSink(tblKind, uploader))){ sink =>
								extractDataBytes{data =>
									onSuccess(data.toMat(sink)(Keep.right).run()){_ =>
										complete(StatusCodes.OK)
									}
								}
							}
						}
					}
				} ~
				getFromBrowseableDirectory(atcMetaSource.getDirectory().toString)
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
				path("etc" / "utcOffset"){
					parameter("stationId"){
						case StationId(stationId) =>
							val offsetJs = service.etcHelper.etcMeta.getUtcOffset(stationId).fold[JsValue](JsNull)(JsNumber(_))
							complete(offsetJs)
						case notStation =>
							complete(StatusCodes.BadRequest -> s"$notStation is not a proper ICOS ETC station id")
					}
				} ~
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
	}

	def reportAccessUri(fut: Future[AccessUri]): Route = onSuccess(fut){au => complete(au.uri.toString)}
}
