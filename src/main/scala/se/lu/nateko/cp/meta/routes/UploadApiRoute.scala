package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.MalformedRequestContentRejection
import akka.http.scaladsl.server.RejectionHandler

import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.JsonSupport._
import se.lu.nateko.cp.meta.core.data.UploadCompletionInfo
import se.lu.nateko.cp.meta.services._
import se.lu.nateko.cp.meta.services.upload._

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
			case MalformedRequestContentRejection(msg, cause) =>
				complete((StatusCodes.BadRequest, msg))
		}.result()
	)

	val Sha256Segment = Segment.flatMap(Sha256Sum.fromString(_).toOption)
	implicit val dataObjectMarshaller = PageContentMarshalling.dataObjectMarshaller
	import AuthenticationRouting.ensureLocalRequest

	def apply(
		service: UploadService,
		authRouting: AuthenticationRouting
	)(implicit mat: Materializer): Route = handleExceptions(errHandler){
		pathPrefix("upload"){
			post{
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
							}
						}
					}
				}
			} ~
			get{
				path("permissions"){
					parameters('submitter, 'userId)((submitter, userId) => {
						val isAllowed: Boolean = service.checkPermissions(new java.net.URI(submitter), userId)
						complete(spray.json.JsBoolean(isAllowed))
					})
				}
			}
		} ~
		(get & path("objects" / Sha256Segment)){ hash =>
			service.fetchDataObj(hash) match{
				case None => complete(StatusCodes.NotFound)
				case Some(dataObj) => complete(dataObj)
			}
		}
	}
}