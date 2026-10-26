package se.lu.nateko.cp.meta.api

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.pattern.after
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
import se.lu.nateko.cp.meta.utils.akkahttp.HttpClient
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.net.URI
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }


object StatisticsClient extends DefaultJsonProtocol {
	case class RestHeartCount(count: Int)
	case class StatsApiCount(downloadCount: Int)
	given RootJsonFormat[StatsApiCount] = jsonFormat1(StatsApiCount.apply)
	given RootJsonFormat[RestHeartCount] = jsonFormat1(RestHeartCount.apply)
}

trait StatisticsProvider {
	implicit def executionContext: ExecutionContext
	def getPreviewCount(dobjHash: Sha256Sum)(using Envri): Future[Option[Int]]
	def getObjDownloadCount(obj: StaticObject)(using Envri): Future[Option[Int]]
	def getCollDownloadCount(uri: URI)(using Envri): Future[Option[Int]]
}

class StatisticsClient(
	val config: StatsClientConfig,
	envriConfs: EnvriConfigs,
	http: HttpClient,
	statsTimeout: FiniteDuration = 10.second
)(implicit system: ActorSystem, mat: Materializer) extends StatisticsProvider {
	import StatisticsClient.*
	private val log = Logging.getLogger(system, this)
	implicit val executionContext: ExecutionContextExecutor = system.dispatcher

	private def dbUri(using Envri) = {
		import config.previews.*
		Uri(s"$baseUri/$dbName")
	}

	private val connPoolSetts = {
		val defPoolSet = ConnectionPoolSettings(system)
		val connSet = defPoolSet.connectionSettings.withConnectingTimeout(100.millis)
		// the default exponential backoff (up to 2 min) after failed connects makes queued requests,
		// and thus landing pages, wait for ever longer when a stats service is down
		defPoolSet.withConnectionSettings(connSet).withMaxRetries(0)
			.withBaseConnectionBackoff(100.millis)
			.withMaxConnectionBackoff(1000.millis)
	}

	private def getStatistic[T : FromEntityUnmarshaller](uri: Uri, dataHost: Option[String] = None): Future[Option[T]] =
		val request = http.singleRequest(
			HttpRequest(uri = uri, headers = dataHost.toSeq.map(Host.apply)),
			settings = connPoolSetts
		)
		val timeout = after(statsTimeout)(Future.failed(TimeoutException(s"no response within $statsTimeout")))
		val statistic = request.flatMap { res =>
			res.status match {
				case StatusCodes.OK =>
					Unmarshal(res.entity).to[T].map(Option(_))
				case s =>
					Unmarshal(res.entity).to[String].flatMap(
						errMsg => Future.failed(new MetadataException(s"$s ($errMsg)"))
					)
			}
		}
		// Keep consuming late responses so timed-out requests release their pool slots.
		Future.firstCompletedOf(Seq(statistic, timeout)).recover{
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
