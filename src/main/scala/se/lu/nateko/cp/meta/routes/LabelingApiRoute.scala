package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.Materializer
import akka.http.scaladsl.server.Route

import scala.util.Success
import scala.util.Failure
import scala.concurrent.duration._

import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.StationLabelingDto
import se.lu.nateko.cp.meta.services.StationLabelingService
import se.lu.nateko.cp.meta.services.UnauthorizedStationUpdateException


object LabelingApiRoute extends CpmetaJsonProtocol{

	def apply(service: StationLabelingService, authRouting: AuthenticationRouting)(implicit mat: Materializer): Route = pathPrefix("labeling"){
		post {
			authRouting.mustBeLoggedIn{ uploader =>
				path("save") {
					entity(as[StationLabelingDto]){uploadMeta =>
						service.saveStationInfo(uploadMeta, uploader) match{
							case Success(datasetUrl) => complete(StatusCodes.OK)
							case Failure(err) => err match{
								case authErr: UnauthorizedStationUpdateException =>
									complete((StatusCodes.Unauthorized, authErr.getMessage))
								case _ => throw err
							}
						}
					} ~
					complete((StatusCodes.BadRequest, "Must provide a valid request payload"))
				} ~
				path("fileupload"){
					extractRequest{ req =>
						val strictFut = req.entity.toStrict(10 second)
			
						onSuccess(strictFut){strict =>
							complete(s"You uploaded ${strict.contentLength} bytes")
						}
					}
					
				}
			}
		} ~
		get{
			pathSingleSlash{
				complete(StaticRoute.fromResource("/www/labeling.html", MediaTypes.`text/html`))
			} ~
			path("login"){
				authRouting.ensureLogin{
					redirect(Uri("/labeling/"), StatusCodes.Found)
				}
			} ~
			pathEnd{
				redirect(Uri("/labeling/"), StatusCodes.Found)
			}
		}
	}
}
