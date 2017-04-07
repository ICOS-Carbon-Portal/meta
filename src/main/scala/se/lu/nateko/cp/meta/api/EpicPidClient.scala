package se.lu.nateko.cp.meta.api

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, Marshal}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.headers.Location
import akka.stream.ActorMaterializer
import se.lu.nateko.cp.meta.{ConfigLoader, EpicPidConfig}
import scala.concurrent.Future
import spray.json._


case class PidEntry(
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

case class PidUpdate(`type`: String, parsed_data: JsValue)

object EpicPidClient{

	def default(implicit system: ActorSystem) = new EpicPidClient(ConfigLoader.default.dataUploadService.epicPid)

	def apply(config: EpicPidConfig)(implicit system: ActorSystem) = new EpicPidClient(config)

	def toUpdate(entry: PidEntry) = PidUpdate(entry.`type`, entry.parsed_data)

	private sealed trait WriteOpResult
	private case object Ok extends WriteOpResult
	private case object PidExists extends WriteOpResult
}

class EpicPidClient(config: EpicPidConfig)(implicit system: ActorSystem) extends DefaultJsonProtocol {

	import EpicPidClient._

	implicit val materializer = ActorMaterializer()
	import system.dispatcher

	implicit val pidExistingFormat = jsonFormat9(PidEntry)
	implicit val pidUpdateFormat = jsonFormat2(PidUpdate)

	private def getHeaders(extraHeaders: List[HttpHeader] = Nil): List[HttpHeader] = {
		val authorization = headers.Authorization(
			BasicHttpCredentials(config.prefix, config.password)
		)

		val accept = headers.Accept(MediaTypes.`application/json`)

		authorization :: accept :: extraHeaders
	}

	private def httpGet(uri: String): Future[HttpResponse] = {
		Http().singleRequest(
			HttpRequest(
				uri = uri,
				headers = getHeaders()
			)
		)
	}

	private def httpSend[T](
		uri: String,
		method: HttpMethod,
		extraHeaders: List[HttpHeader] = Nil,
		payload: T
	) (implicit m: Marshaller[T, RequestEntity]): Future[HttpResponse] =
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

	private def httpDelete(uri: String): Future[HttpResponse] = {
		Http().singleRequest(
			HttpRequest(
				method = HttpMethods.DELETE,
				uri = uri,
				headers = getHeaders()
			)
		)
	}

	def getPid(suffix: String): String = config.prefix + "/" + suffix

	def list: Future[Seq[String]] = {
		httpGet(config.url + config.prefix).flatMap(
			resp => resp.status match {
				case StatusCodes.OK => Unmarshal(resp.entity).to[Seq[String]]
				case _ => Future.failed(new Exception(s"Got ${resp.status} from the server"))
			}
		)
	}

	def get(suffix: String): Future[Seq[PidEntry]] = {
		httpGet(config.url + getPid(suffix)).flatMap(
			resp => resp.status match {
				case StatusCodes.OK => Unmarshal(resp.entity).to[Seq[PidEntry]]
				case _ => Future.failed(new Exception(s"Got ${resp.status} from the server"))
			}
		)
	}

	def update(suffix: String, updates: Seq[PidUpdate]): Future[Unit] = {
		httpSend(
			uri = config.url + getPid(suffix),
			method = HttpMethods.PUT,
			extraHeaders = List(headers.`If-Match`.*),
			payload = updates
		).map(resp =>
			if (resp.status != StatusCodes.NoContent) {
				throw new Exception(s"Got ${resp.status} from the server")
			}
		)
	}

	def createNew(suffix: String, newEntries: Seq[PidUpdate]): Future[Unit] =
		createOrReportExistence(suffix, newEntries).flatMap{
			case Ok =>
				Future.successful(())
			case PidExists => Future.failed(
				new Exception(s"Failed to mint pid '${getPid(suffix)}'. Make sure it does not exist already.")
			)
		}

	def createOrRecreate(suffix: String, newEntries: Seq[PidUpdate]): Future[Unit] =
		createOrReportExistence(suffix, newEntries).flatMap{
			case Ok =>
				Future.successful(())
			case PidExists =>
				delete(suffix).flatMap(_ => createNew(suffix, newEntries))
		}

	private def createOrReportExistence(suffix: String, newEntries: Seq[PidUpdate]): Future[WriteOpResult] = {
		val pid = getPid(suffix)
		httpSend(
			uri = config.url + pid,
			method = HttpMethods.PUT,
			extraHeaders = List(headers.`If-None-Match`.*),
			payload = newEntries
		).flatMap(resp => resp.status match {
			case StatusCodes.Created =>
				Future.successful(Ok)
			case StatusCodes.PreconditionFailed =>
				Future.successful(PidExists)
			case _ =>
				Future.failed(new Exception(s"Unexpectedly got '${resp.status}' from the EPIC PID server while minting '$pid'"))
		})
	}

	def createRandom(newEntries: Seq[PidUpdate]): Future[String] = {

		httpSend(
			uri = config.url + config.prefix + "/",
			method = HttpMethods.POST,
			payload = newEntries
		).map(resp =>
			if (resp.status != StatusCodes.Created) {
				throw new Exception(s"Got ${resp.status} from the server")
			} else {
				resp.header[Location].getOrElse(
					throw new Exception("'Location' was not found in the response header")
				).value.split("/").last
			}
		)
	}

	def delete(suffix: String): Future[Unit] = {
		val pid = getPid(suffix)
		httpDelete(config.url + pid)
			.map(resp =>
				if(resp.status != StatusCodes.NoContent) {
					throw new Exception(s"Got ${resp.status} from the server while trying to delete $pid")
				}
			)
	}

}
