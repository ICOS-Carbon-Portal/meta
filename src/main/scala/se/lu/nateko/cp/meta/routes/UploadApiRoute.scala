package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.Materializer
import akka.http.scaladsl.server.ExceptionHandler

import scala.util.Success
import scala.util.Failure

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.services._
import se.lu.nateko.cp.meta.services.upload._
import se.lu.nateko.cp.meta.UploadMetadataDto

object UploadApiRoute extends CpmetaJsonProtocol{

	private val errHandler = ExceptionHandler{
		case authErr: UnauthorizedUploadException =>
			complete((StatusCodes.Unauthorized, authErr.getMessage))
		case userErr: UploadUserErrorException =>
			complete((StatusCodes.BadRequest, userErr.getMessage))
		case err => throw err
	}

	val Sha256Segment = Segment.flatMap(Sha256Sum.fromString(_).toOption)
	implicit val dataObjectMarshaller = LandingPageMarshalling.marshaller
	import AuthenticationRouting.ensureLocalRequest

	def apply(
		service: UploadService,
		authRouting: AuthenticationRouting
	)(implicit mat: Materializer): Route = handleExceptions(errHandler){
		pathPrefix("upload"){
			post{
				path(Sha256Segment){hash =>
					ensureLocalRequest{
						onSuccess(service.completeUpload(hash)){complete(_)}
					}
				} ~
				pathEnd{
					authRouting.mustBeLoggedIn{uploader =>
						entity(as[UploadMetadataDto]){uploadMeta =>
							complete(service.registerUpload(uploadMeta, uploader))
						} ~
						complete((StatusCodes.BadRequest, "Must provide a valid request payload"))
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
			service.packageFetcher.fetch(hash) match{
				case None => complete(StatusCodes.NotFound)
				case Some(dataObj) => complete(dataObj)
			}
		}
	}
}