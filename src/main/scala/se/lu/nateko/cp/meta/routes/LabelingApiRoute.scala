package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import se.lu.nateko.cp.meta.services.StationLabelingService
import akka.stream.Materializer
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.meta.StationLabelingDto
import scala.util.Success
import scala.util.Failure
import scala.concurrent.duration._


object LabelingApiRoute extends CpmetaJsonProtocol{

	def apply(service: StationLabelingService, authRouting: AuthenticationRouting)(implicit mat: Materializer): Route = path("labeling" / "save"){
		post{
				entity(as[StationLabelingDto]){uploadMeta =>
					service.saveStationInfo(uploadMeta, null) match{
						case Success(datasetUrl) => complete(StatusCodes.OK)
						case Failure(err) => err match{
//								case authErr: UnauthorizedUploadException =>
//									complete((StatusCodes.Unauthorized, authErr.getMessage))
//								case userErr: UploadUserErrorException =>
//									complete((StatusCodes.BadRequest, userErr.getMessage))
							case _ => throw err
						}
					}
				} ~
				complete((StatusCodes.BadRequest, "Must provide a valid request payload"))
//			authRouting.mustBeLoggedIn{uploader =>
//			}
		}
	} ~
	(post & path("labeling" / "fileupload")){

		extractRequest{ req =>
			val strictFut = req.entity.toStrict(10 second)

			onSuccess(strictFut){strict =>
				complete(s"You uploaded ${strict.contentLength} bytes")
			}
		}
		
	}
}
