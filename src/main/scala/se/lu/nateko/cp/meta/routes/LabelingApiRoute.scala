package se.lu.nateko.cp.meta.routes

import spray.json.JsObject

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart, StatusCodes}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import java.net.URI
import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions
import scala.util.Try
import se.lu.nateko.cp.meta.services.labeling.{StationLabelingHistory, StationLabelingService}
import se.lu.nateko.cp.meta.services.{IllegalLabelingStatusException, UnauthorizedStationUpdateException, UnauthorizedUserInfoUpdateException}
import se.lu.nateko.cp.meta.{CpmetaJsonProtocol, FileDeletionDto, LabelingStatusUpdate, LabelingUserDto}

object LabelingApiRoute extends CpmetaJsonProtocol:

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
		service: Option[StationLabelingService], authRouting: AuthenticationRouting, adminUsers: Seq[String]
	)(using Materializer): Route =
		pathPrefix("labeling"){
			service match
				case Some(lblService) =>
					handleExceptions(exceptionHandler){inner(lblService, authRouting, adminUsers)}
				case None =>
					inline def msg = "Labeling service has not been enabled on this server"
					complete(StatusCodes.ServiceUnavailable -> msg)
		}

	private def inner(
		service: StationLabelingService, authRouting: AuthenticationRouting, adminUsers: Seq[String]
	)(using mat: Materializer): Route =

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
				} ~
				path("testemailing"):
					parameter("address".optional): addr =>
						authRouting.allowUsers(adminUsers):
							service.sendTestEmail(addr)
							complete(StatusCodes.OK)
			}
		} ~
		get:
			path("userinfo"):
				authRouting.mustBeLoggedIn: user =>
					complete(service.getLabelingUserInfo(user))
			~
			path("filepack" / Segment): _ =>
				parameter("stationId".as[URI]): stationId =>
					complete(service.getFilePack(stationId))
			~
			path("labelingHistory.csv" | "labelingHistory"):
				import StationLabelingHistory.*
				val src = Source(
					CsvHeader +: service.labelingHistory.map(toCsvRow)
				).map(ByteString(_, StandardCharsets.UTF_8))

				complete(HttpEntity(ContentTypes.`text/csv(UTF-8)`, src))

	end inner

end LabelingApiRoute
