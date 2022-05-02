package se.lu.nateko.cp.meta.routes

import scala.language.implicitConversions

import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.ResponseEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.FileDeletionDto
import se.lu.nateko.cp.meta.LabelingStatusUpdate
import se.lu.nateko.cp.meta.LabelingUserDto
import se.lu.nateko.cp.meta.services.IllegalLabelingStatusException
import se.lu.nateko.cp.meta.services.UnauthorizedStationUpdateException
import se.lu.nateko.cp.meta.services.UnauthorizedUserInfoUpdateException
import se.lu.nateko.cp.meta.services.labeling.StationLabelingHistory
import se.lu.nateko.cp.meta.services.labeling.StationLabelingService
import spray.json.JsObject

import java.net.URI
import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Try
import spray.json.RootJsonReader
import spray.json.DefaultJsonProtocol.RootJsObjectFormat

object LabelingApiRoute extends CpmetaJsonProtocol{

	private val exceptionHandler = ExceptionHandler{

		case authErr: UnauthorizedStationUpdateException =>
			complete((StatusCodes.Unauthorized, authErr.getMessage))

		case authErr: UnauthorizedUserInfoUpdateException =>
			complete((StatusCodes.Unauthorized, authErr.getMessage))

		case authErr: IllegalLabelingStatusException =>
			complete((StatusCodes.BadRequest, authErr.getMessage))

		case err => throw err
	}

	private given Unmarshaller[String, URI] = Unmarshaller(_ => s => Future.fromTry(Try{new URI(s)}))

	def apply(
		service: StationLabelingService,
		authRouting: AuthenticationRouting
	)(implicit mat: Materializer): Route = (handleExceptions(exceptionHandler) & pathPrefix("labeling")){

		implicit val ctxt = mat.executionContext

		post {
			authRouting.mustBeLoggedIn{ uploader =>
				path("save") {
					entity(as[JsObject]){uploadMeta =>
						service.saveStationInfo(uploadMeta, uploader)
						complete(StatusCodes.OK)
					}
				} ~
				path("updatestatus"){
					entity(as[LabelingStatusUpdate]){update =>
						service.updateStatus(
							update.stationUri,
							update.newStatus,
							update.newStatusComment.map(_.trim).filter(!_.isEmpty),
							uploader
						).get
						complete(StatusCodes.OK)
					}
				} ~
				path("saveuserinfo") {
					entity(as[LabelingUserDto]){userInfo =>
						service.saveUserInfo(userInfo, uploader)
						complete(StatusCodes.OK)
					}
				} ~
				path("fileupload"){
					entity(as[Multipart.FormData]){ fdata =>
						onSuccess(fdata.toStrict(1.hour)){strictFormData =>
							onSuccess(service.processFile(strictFormData, uploader)){
								complete(StatusCodes.OK)
							}
						}
					}
				} ~
				path("filedeletion"){
					entity(as[FileDeletionDto]){ fileInfo =>
						service.deleteFile(fileInfo.stationUri, fileInfo.file, uploader)
						complete(StatusCodes.OK)
					}
				}
			}
		} ~
		get{
			path("userinfo"){
				authRouting.mustBeLoggedIn{ user =>
					complete(service.getLabelingUserInfo(user))
				}
			} ~
			path("filepack" / Segment){ _ =>
				parameter("stationId".as[URI]){stationId =>
					complete(service.getFilePack(stationId))
				}
			} ~
			path("labelingHistory.csv" | "labelingHistory"){
				import StationLabelingHistory.*
				val src = Source(
					CsvHeader +: service.labelingHistory.toSeq.sortBy(_.station.provId).map(toCsvRow)
				).map(ByteString(_, StandardCharsets.UTF_8))

				complete(HttpEntity(ContentTypes.`text/csv(UTF-8)`, src))
			}
		}
	}

}
