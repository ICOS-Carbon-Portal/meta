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
import akka.stream.scaladsl.Sink
import scala.concurrent.Future
import akka.util.ByteString
import java.net.URI
import se.lu.nateko.cp.meta.services.UploadedFile
import se.lu.nateko.cp.meta.FileDeletionDto
import akka.http.scaladsl.server.directives.ContentTypeResolver



object LabelingApiRoute extends CpmetaJsonProtocol{

	def apply(service: StationLabelingService, authRouting: AuthenticationRouting)(implicit mat: Materializer): Route = pathPrefix("labeling"){
		implicit val ctxt = mat.executionContext
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
					entity(as[Multipart.FormData]){ fdata =>

						onSuccess(fdata.toStrict(1 hour)){strictFormData =>
							val nameToParts = strictFormData.strictParts.map(part => (part.name, part)).toMap
							val fileType = nameToParts("fileType").entity.data.decodeString("UTF-8")
							val stationUri = nameToParts("stationUri").entity.data.decodeString("UTF-8")
							val filePart = nameToParts("uploadedFile")
							val fileName = filePart.filename.get
							val fileContent = filePart.entity.data

							val fileInfo = UploadedFile(new URI(stationUri), fileName, fileType, fileContent)
							val doneFut = service.processFile(fileInfo, uploader)

							onSuccess(doneFut){
								complete(StatusCodes.OK)
							}
						}
					}
					
				} ~
				path("filedeletion"){
					entity(as[FileDeletionDto]){ fileInfo =>
						service.deleteFile(fileInfo.stationUri, fileInfo.file, uploader).get
						complete(StatusCodes.OK)
					}
				}
			}
		} ~
		get{
			pathSingleSlash{
				getFromResource("www/labeling.html")
			} ~
			path("login"){
				authRouting.ensureLogin{
					redirect(Uri("/labeling/"), StatusCodes.Found)
				}
			} ~
			pathEnd{
				redirect(Uri("/labeling/"), StatusCodes.Found)
			} ~
			path("userinfo"){
				authRouting.mustBeLoggedIn{ user =>
					complete(service.getLabelingUserInfo(user))
				}
			}
		}
	} ~
	path("files" / Segment / Segment){ (hash, fileName) =>
		val contentResolver = implicitly[ContentTypeResolver]
		val contentType = contentResolver(fileName)
		val file = service.fileService.getPath(hash).toFile
		getFromFile(file, contentType)
	}

}
