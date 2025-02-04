package se.lu.nateko.cp.meta.api

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{ HttpRequest, StatusCodes, Uri }
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal}
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.StatsClientConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{EnvriConfigs, StaticObject}
import se.lu.nateko.cp.meta.services.MetadataException
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.net.URI
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContextExecutor, Future }


object StatisticsClient extends DefaultJsonProtocol {
	case class RestHeartCount(count: Int)
	case class StatsApiCount(downloadCount: Int)
	given RootJsonFormat[StatsApiCount] = jsonFormat1(StatsApiCount.apply)
	given RootJsonFormat[RestHeartCount] = jsonFormat1(RestHeartCount.apply)
}

class StatisticsClient(val config: StatsClientConfig, envriConfs: EnvriConfigs)(implicit system: ActorSystem, mat: Materializer) {
	import StatisticsClient.*
	private val http = Http()
	private val log = Logging.getLogger(system, this)
	implicit val executionContext: ExecutionContextExecutor = system.dispatcher

	private def dbUri(using Envri) = {
		import config.previews.*
		Uri(s"$baseUri/$dbName")
	}

	private val connPoolSetts = {
		val defPoolSet = ConnectionPoolSettings(system)
		val connSet = defPoolSet.connectionSettings.withConnectingTimeout(20.millis)
		defPoolSet.withConnectionSettings(connSet).withMaxRetries(0)
	}

	private def getStatistic[T : FromEntityUnmarshaller](uri: Uri, dataHost: Option[String] = None): Future[Option[T]] = http
		.singleRequest(
			HttpRequest(uri = uri, headers = dataHost.toSeq.map(Host.apply)),
			settings = connPoolSetts
		)
		.flatMap { res =>
			res.status match {
				case StatusCodes.OK =>
					Unmarshal(res.entity).to[T].map(Option(_))
				case s =>
					Unmarshal(res.entity).to[String].flatMap(
						errMsg => Future.failed(new MetadataException(s"$s ($errMsg)"))
					)
			}
		}.recover{
			case err: Throwable =>
				log.warning(s"Problem fetching statistics (${err.getMessage})\nfrom: $uri")
				None
		}

	def getPreviewCount(dobjHash: Sha256Sum)(using Envri): Future[Option[Int]] = {
		getStatistic[Seq[RestHeartCount]](s"$dbUri/portaluse/_aggrs/getPreviewCountForPid?avars={'pid':'${dobjHash.id}'}&np")
			.map(_.map(_.map(_.count).sum))
	}

	def getObjDownloadCount(obj: StaticObject)(using Envri): Future[Option[Int]] =
		getDownloadCount(obj.hash.base64Url)

	def getCollDownloadCount(uri: URI)(using Envri): Future[Option[Int]] =
		getDownloadCount(uri.getPath.split('/').last)

	private def getDownloadCount(hash: String)(using envri: Envri): Future[Option[Int]] = {
		val uri = Uri(config.downloadsUri).withQuery(Uri.Query("hashId" -> hash))
		val dataHost = envriConfs.get(envri).map(_.dataHost)
		getStatistic[Seq[StatsApiCount]](uri, dataHost).map(_.map(_.map(_.downloadCount).sum))
	}

}
