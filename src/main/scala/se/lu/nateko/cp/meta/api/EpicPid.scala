package se.lu.nateko.cp.meta.api

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, Marshal}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.stream.ActorMaterializer
import se.lu.nateko.cp.meta.{ConfigLoader, EpicPidConfig}
import scala.concurrent.Future

case class PidExisting(
	idx: Int,
	`type`: String,
	parsed_data: JsValue,
	data: String,
	timestamp: String,
	ttl_type: Int,
	ttl: Int,
	refs: Seq[String],
	privs: String
)

case class PidUpdate(
	`type`: String,
	parsed_data: JsValue
)

class EpicPid(config: EpicPidConfig)(implicit system: ActorSystem) extends DefaultJsonProtocol {

	implicit val materializer = ActorMaterializer()
	import system.dispatcher

	implicit val pidExistingFormat = jsonFormat9(PidExisting)
	implicit val pidUpdateFormat = jsonFormat2(PidUpdate)

	private def getHeaders(extraHeaders: List[HttpHeader] = Nil): List[HttpHeader] = {
		val authorization = headers.Authorization(
			BasicHttpCredentials(config.userName, config.password)
		)

		val accept = headers.Accept(MediaTypes.`application/json`)

		authorization :: accept :: extraHeaders
	}

	private def getResponse(uri: String, headers: List[HttpHeader]): Future[HttpResponse] = {
		Http().singleRequest(
			HttpRequest(
				uri = uri,
				headers = headers
			)
		)
	}

	private def send[T](uri: String, method: HttpMethod, extraHeaders: List[HttpHeader] = Nil, payload: T)(implicit m: Marshaller[T, RequestEntity]): Future[HttpResponse] =
		Marshal(payload).to[RequestEntity].flatMap(entity =>
			Http().singleRequest(
				HttpRequest(
					method = method,
					uri = uri,
					headers = getHeaders(extraHeaders),
					entity = entity
				)
			)
		)

	private def delete(uri: String): Future[Unit] = {
		Http().singleRequest(
			HttpRequest(
				method = HttpMethods.DELETE,
				uri = uri,
				headers = getHeaders()
			)
		).map(resp => if(resp.status != StatusCodes.NoContent)
			throw new Exception(s"Got ${resp.status} from the server")
		)
	}

	def listPids(): Future[Seq[String]] = {
		getResponse(config.url + config.userName, getHeaders()).flatMap(
			resp => resp.status match {
				case StatusCodes.OK => Unmarshal (resp.entity).to[Seq[String]]
				case _ => Future.failed(new Exception(s"Got ${resp.status} from the server"))
			}
		)
	}

	def getPid(pidName: String): Future[Seq[PidExisting]] = {
		getResponse(config.url + config.userName + "/" + pidName, getHeaders()).flatMap(
			resp => resp.status match {
				case StatusCodes.OK => Unmarshal(resp.entity).to[Seq[PidExisting]]
				case _ => Future.failed(new Exception(s"Got ${resp.status} from the server"))
			}
		)
	}

	def editPid(pidName: String, newPid: Seq[PidUpdate]): Future[Unit] = {
		send(
			uri = config.url + config.userName + "/" + pidName,
			method = HttpMethods.PUT,
			extraHeaders = List(headers.`If-Match`.*),
			payload = newPid
		).map(resp => if (resp.status != StatusCodes.NoContent) {
			throw new Exception(s"Got ${resp.status} from the server")
		})
	}

	def createPidWithName(pidName: String, newPid: Seq[PidUpdate]): Future[Unit] = {

		send(
			uri = config.url + config.userName + "/" + pidName,
			method = HttpMethods.PUT,
			extraHeaders = List(headers.`If-None-Match`.*),
			payload = newPid
		).map(resp => if (resp.status != StatusCodes.Created) {
			throw new Exception(s"Got ${resp.status} from the server")
		})

	}

	def createPid(newPid: Seq[PidUpdate]): Future[String] = {

		send(
			uri = config.url + config.userName + "/",
			method = HttpMethods.POST,
			payload = newPid
		).map(resp => if (resp.status != StatusCodes.Created) {
			throw new Exception(s"Got ${resp.status} from the server")
		} else {
			resp.getHeader("Location").getOrElse(
				throw new Exception("'Location' was not found in the response header")
			).value
		})

	}

	def deletePid(pidName: String): Future[Unit] = {
		delete(config.url + config.userName + "/" + pidName)
	}

}

object EpicPid{

	def default(implicit system: ActorSystem): EpicPid = new EpicPid(ConfigLoader.default.dataUploadService.epicPid)
	def apply(config: EpicPidConfig)(implicit system: ActorSystem) = new EpicPid(config)

}