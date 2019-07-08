package se.lu.nateko.cp.meta

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
import scala.concurrent.ExecutionContext
import akka.http.scaladsl.model.headers.Host
import spray.json.RootJsonFormat
import akka.http.scaladsl.marshalling.Marshaller

class CpUploadClient(token: String, conf: CpUploadClient.Config)(implicit val ctxt: ExecutionContext) extends CpmetaJsonProtocol{

	// val metaUri = Uri("https://meta.icos-cp.eu/upload")
	// val dataUri = Uri("https://data.icos-cp.eu/objects/")
	private val cookie = Cookie("cpauthToken", token)
	private val metaHost = Host("meta.icos-cp.eu")
	private val dataHost = Host("data.icos-cp.eu")

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

	def fileUploadReq(file: CpUploadClient.FileInfo): HttpRequest = {
		val entity = HttpEntity(ContentTypes.`application/octet-stream`, FileIO.fromPath(file.path))
		HttpRequest(
			method = HttpMethods.PUT,
			uri = Uri("/objects/" + file.hash.id).resolvedAgainst(conf.dataBase),
			headers = Seq(cookie, dataHost),
			entity = entity
		)
	}

}

object CpUploadClient{
	class FileInfo(val path: Path, val hash: Sha256Sum)

	class Config(val metaBase: Uri, val dataBase: Uri)
}
