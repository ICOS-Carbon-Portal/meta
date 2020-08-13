package se.lu.nateko.cp.meta.upload

import scala.concurrent.Future
import scala.collection.immutable.Seq
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import java.nio.file.Path
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import akka.stream.scaladsl.FileIO
import akka.http.scaladsl.model.headers.Host
import spray.json.RootJsonFormat
import se.lu.nateko.cp.meta.utils.akkahttp.responseToDone
import se.lu.nateko.cp.meta.utils.async.executeSequentially
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.Done
import akka.stream.Materializer
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.StaticCollectionDto
import java.net.URI
import akka.http.scaladsl.unmarshalling.Unmarshal

class CpUploadClient(conf: CpUploadClient.Config)(implicit val system: ActorSystem) extends CpmetaJsonProtocol{

	import system.dispatcher
	import CpUploadClient._

	private val cookie = Cookie("cpauthToken", conf.cpauthToken)
	private val metaHost = Host(conf.metaHost)
	private val dataHost = Host(conf.dataHost)
	private val http = Http()


	def metaUploadReq[T <: UploadDto : RootJsonFormat](dto: T): Future[HttpRequest] = Marshal(dto)
		.to[RequestEntity]
		.map{entity => HttpRequest(
				method = HttpMethods.POST,
				uri = Uri("/upload").resolvedAgainst(conf.metaBase),
				headers = Seq(cookie, metaHost),
				entity = entity
		)}

	def objMetaUploadReq(dto: ObjectUploadDto) = metaUploadReq(dto)
	def collUploadReq(dto: StaticCollectionDto) = metaUploadReq(dto)

	def fileUploadReq(file: FileInfo): HttpRequest = {
		val entity = HttpEntity(ContentTypes.`application/octet-stream`, FileIO.fromPath(file.path))
		HttpRequest(
			method = HttpMethods.PUT,
			uri = Uri("/objects/" + file.hash.id).resolvedAgainst(conf.dataBase),
			headers = Seq(cookie, dataHost),
			entity = entity
		)
	}

	def uploadSingleMeta[T <: UploadDto : RootJsonFormat](dto: T): Future[Done] = metaUploadReq(dto)
		.flatMap(req => http.singleRequest(req))
		.flatMap(responseToDone)

	def uploadSingleObjMeta(dto: ObjectUploadDto) = uploadSingleMeta(dto)
	def uploadSingleCollMeta(dto: StaticCollectionDto) = uploadSingleMeta(dto)

	def uploadSingleObject(dto: ObjectUploadDto, file: FileInfo): Future[Done] = uploadSingleMeta(dto).flatMap(_ => uploadSingleFile(file))

	def uploadMultiMetas(dtos: Seq[ObjectUploadDto]): Future[Done] = executeSequentially(dtos)(uploadSingleMeta)
	def uploadMultiObjs(objs: Seq[ObjectUploadInfo]) = executeSequentially(objs){case (dto, file) => uploadSingleObject(dto, file)}

	def uploadSingleFile(file: FileInfo): Future[Done] = http
		.singleRequest(fileUploadReq(file))
		.flatMap(responseToDone)

	def getUploadDto[T <: UploadDto : RootJsonFormat](landingPage: URI): Future[T] = http
		.singleRequest{HttpRequest(
			uri = Uri("/dtodownload").resolvedAgainst(conf.metaBase).withQuery(Uri.Query("uri" -> landingPage.toString))
		)}
		.flatMap{resp =>
			Unmarshal(resp).to[T]
		}

	def reIngestObject(hash: Sha256Sum): Future[Done] = http
		.singleRequest(HttpRequest(
			uri = Uri("/objects/" + hash.id).resolvedAgainst(conf.dataBase),
			method = HttpMethods.POST,
			headers = Seq(cookie, dataHost)
		))
		.flatMap(responseToDone)
}

object CpUploadClient{
	class FileInfo(val path: Path, val hash: Sha256Sum)
	type ObjectUploadInfo = (ObjectUploadDto, FileInfo)

	case class Config(
		cpauthToken: String, metaHost: String, dataHost: String,
		customMetaBase: Option[Uri] = None, customDataBase: Option[Uri] = None
	){
		def metaBase: Uri = customMetaBase.getOrElse(Uri("https://" + metaHost))
		def dataBase: Uri = customMetaBase.getOrElse(Uri("https://" + dataHost))
	}
}
