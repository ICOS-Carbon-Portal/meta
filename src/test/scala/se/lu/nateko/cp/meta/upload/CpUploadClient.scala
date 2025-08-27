package se.lu.nateko.cp.meta.upload

import scala.language.unsafeNulls

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.{Accept, Cookie, Host}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, MediaTypes, RequestEntity, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.utils.akkahttp.responseToDone
import se.lu.nateko.cp.meta.utils.async.executeSequentially
import se.lu.nateko.cp.meta.{CpmetaJsonProtocol, ObjectUploadDto, StaticCollectionDto, UploadDto}
import spray.json.RootJsonFormat

import java.net.URI
import java.nio.file.Path
import scala.collection.immutable.Seq
import scala.concurrent.Future

class CpUploadClient(conf: CpUploadClient.Config)(implicit val system: ActorSystem) extends CpmetaJsonProtocol{

	import system.dispatcher
	import CpUploadClient.*

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
		.flatMap(responseToDone("uploading single meta"))

	def uploadSingleObjMeta(dto: ObjectUploadDto) = uploadSingleMeta(dto)
	def uploadSingleCollMeta(dto: StaticCollectionDto) = uploadSingleMeta(dto)

	def uploadSingleObject(dto: ObjectUploadDto, file: FileInfo): Future[Done] = uploadSingleMeta(dto).flatMap(_ => uploadSingleFile(file))

	def uploadMultiMetas(dtos: Seq[ObjectUploadDto]): Future[Done] = executeSequentially(dtos)(dto => uploadSingleMeta(dto)).map(_ => Done)
	def uploadMultiObjs(objs: Seq[ObjectUploadInfo]) = executeSequentially(objs){case (dto, file) => uploadSingleObject(dto, file)}

	def uploadSingleFile(file: FileInfo): Future[Done] = http
		.singleRequest(fileUploadReq(file))
		.flatMap(responseToDone("uploading single file"))

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
		.flatMap(responseToDone(s"re-ingesting $hash"))

	def fetchDataObject(uri: java.net.URI): Future[DataObject] = http
		.singleRequest(HttpRequest(
			uri = Uri(uri.toASCIIString),
			headers = Seq(Accept(MediaTypes.`application/json`))
		))
		.flatMap{resp =>
			Unmarshal(resp).to[DataObject]
		}

	def getFileInfo(path: Path): Future[FileInfo] = FileIO.fromPath(path)
		.runWith(DigestFlow.sha256.to(Sink.ignore)).map{hash =>
			new FileInfo(path, hash)
		}
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
