package se.lu.nateko.cp.meta.api

import java.net.URI
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{ HttpRequest, StatusCodes, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import se.lu.nateko.cp.meta.RestheartConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.services.MetadataException
import spray.json.DefaultJsonProtocol
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.core.data.DocObject
import se.lu.nateko.cp.meta.utils.async.timeLimit
import akka.http.scaladsl.settings.ConnectionPoolSettings

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

	private[this] val connPoolSetts = {
		val defPoolSet = ConnectionPoolSettings(system)
		val connSet = defPoolSet.connectionSettings.withConnectingTimeout(21.millis)
		defPoolSet.withConnectionSettings(connSet)
	}

	private def getStatistic(uri: Uri): Future[Option[Int]] = {
			http.singleRequest(HttpRequest(uri = uri)).andThen{
				case scala.util.Failure(err) => println(s"${err.getMessage} for $uri")
			}
		.flatMap { res =>
			res.status match {
				case StatusCodes.OK =>
					Unmarshal(res.entity).to[Seq[Statistics]].map(sumCounts)
				case s =>
					Unmarshal(res.entity).to[String].flatMap(
						errMsg => Future.failed(new MetadataException(s"$s ($errMsg)"))
					)
			}
		}.recover{
			case err: Throwable =>
				system.log.warning(s"Problem fetching statistics (${err.getMessage})\nfrom: $uri")
				None
		}
	}

	def getPreviewCount(dobjHash: Sha256Sum)(implicit envri: Envri): Future[Option[Int]] = {
		getStatistic(s"$dbUri/portaluse/_aggrs/getPreviewCountForPid?avars={'pid':'${dobjHash.id}'}&np")
	}

	def getObjDownloadCount(obj: StaticObject)(implicit envri: Envri): Future[Option[Int]] = {
		val doc = obj match{
			case _: DocObject => "Doc"
			case _ => ""
		}
		getStatistic(s"$dbUri/dobjdls/_aggrs/get${doc}DownloadCountForSHA256?avars={'pid':'${obj.hash.base64Url}'}&np")
	}

	def getCollDownloadCount(uri: URI)(implicit envri: Envri): Future[Option[Int]] = {
		getStatistic(s"$dbUri/colldls/_aggrs/getDownloadCountForUri?avars={'uri':'$uri'}&np")
	}

	private def sumCounts(stats: Seq[Statistics]): Option[Int] = Some(stats.map(_.count).sum)
}
