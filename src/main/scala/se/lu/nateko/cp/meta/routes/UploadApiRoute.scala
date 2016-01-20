package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.Materializer
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import scala.util.Success
import scala.util.Failure
import se.lu.nateko.cp.meta.services._
import akka.http.scaladsl.server.ExceptionHandler

object UploadApiRoute extends CpmetaJsonProtocol{

	private val errHandler = ExceptionHandler{
		case authErr: UnauthorizedUploadException =>
			complete((StatusCodes.Unauthorized, authErr.getMessage))
		case userErr: UploadUserErrorException =>
			complete((StatusCodes.BadRequest, userErr.getMessage))
		case err => throw err
	}

	def apply(
		service: UploadService,
		authRouting: AuthenticationRouting
	)(implicit mat: Materializer): Route = handleExceptions(errHandler){
		pathPrefix("upload"){
			post{
				authRouting.mustBeLoggedIn{uploader =>
					pathEnd{
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
		(get & path("object" / Segment)){ hashStr =>
			val hash = Sha256Sum.fromBase64Url(hashStr).get
			val dataPackage = service.packageFetcher.getPackage(hash)
			val pageHtml = LandingPageBuilder.getPage(dataPackage)
			complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, pageHtml))
		}
	}
}