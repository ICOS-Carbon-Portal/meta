package se.lu.nateko.cp.meta.api

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{ HttpRequest, StatusCodes, Uri }
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal}
import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
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
	/** Keeps the akka-http pool from letting its exponential connection backoff grow into minutes */
	private val MaxConnBackoff = 1.second
	/** Number of consecutive failures after which statistics requests are skipped altogether */
	private val MaxFailures = 3
	private val CallTimeout = 10.seconds
	/** How long statistics requests stay skipped before the stats server is probed again */
	private val ResetTimeout = 1.minute

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
		// while the stats server is down, the pool backs off exponentially between connection
		// attempts, and requests simply wait for that backoff to expire. Left at its default
		// maximum of 2 minutes, that makes every landing page slower to load than the previous one.
		defPoolSet
			.withConnectionSettings(connSet)
			.withMaxRetries(0)
			.withMaxConnectionBackoff(MaxConnBackoff)
	}

	// statistics are optional extras on the landing pages, so rather than making every page
	// pay the price of an unavailable stats server, skip the requests until it is back
	private val breaker = CircuitBreaker(system.scheduler, MaxFailures, CallTimeout, ResetTimeout)
		.onOpen(log.warning("Statistics server seems to be unavailable, pausing statistics fetching"))
		.onClose(log.info("Statistics server is responding again, resuming statistics fetching"))

	private def getStatistic[T : FromEntityUnmarshaller](uri: Uri, dataHost: Option[String] = None): Future[Option[T]] = breaker
		.withCircuitBreaker{
			http.singleRequest(
				HttpRequest(uri = uri, headers = dataHost.toSeq.map(Host.apply)),
				settings = connPoolSetts
			)
			.flatMap { res =>
				res.status match {
					case StatusCodes.OK =>
						Unmarshal(res.entity).to[T]
					case s =>
						Unmarshal(res.entity).to[String].flatMap(
							errMsg => Future.failed(new MetadataException(s"$s ($errMsg)"))
						)
				}
			}
		}
		.map(Option(_))
		.recover{
			case _: CircuitBreakerOpenException =>
				log.debug(s"Skipped fetching statistics from $uri, statistics server considered unavailable")
				None
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
