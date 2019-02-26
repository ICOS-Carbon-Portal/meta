package se.lu.nateko.cp.meta.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import se.lu.nateko.cp.meta.RestheartConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import spray.json.DefaultJsonProtocol

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.control.NoStackTrace

case class Statistics(count: Int)

object Statistics extends DefaultJsonProtocol {
	implicit val statsFormat = jsonFormat1(Statistics.apply)
}

class StatisticsClient(val config: RestheartConfig)(implicit system: ActorSystem, mat: Materializer) {

	private val http = Http()
	implicit val executionContext: ExecutionContextExecutor = system.dispatcher

	private def dbUri(implicit envri: Envri) = {
		import config._
		Uri(s"$baseUri/$dbName")
	}

	private def getStatistic(uri: Uri): Future[Option[Int]] = {
		http.singleRequest(
			HttpRequest(uri = uri)
		).flatMap { res =>
			res.status match {
				case StatusCodes.OK =>
					Unmarshal(res.entity).to[Seq[Statistics]].map(_.map(_.count).headOption)
				case s =>
					Unmarshal(res.entity).to[String].flatMap(
						errMsg => Future.failed(new Exception(errMsg) with NoStackTrace)
					)
			}
		}.recover{
			case err: Throwable =>
				system.log.warning("Problem fetching statistics. " + err.getMessage)
				None
		}
	}

	def getPreviewCount(dobjHash: Sha256Sum)(implicit envri: Envri): Future[Option[Int]] = {
		getStatistic(s"$dbUri/portaluse/_aggrs/getPreviewCountForPid?avars={'pid':'${dobjHash.id}'}&np")
	}

	def getDownloadCount(dobjHash: Sha256Sum)(implicit envri: Envri): Future[Option[Int]] = {
		getStatistic(s"$dbUri/dobjdls/_aggrs/getDownloadCountForSHA256?avars={'pid':'${dobjHash.base64Url}'}&np")
	}
}
