package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.{HttpRequest, MediaTypes}
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.core.data.{References, StaticObject}
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.services.ExternalProviders

import java.net.URI
import java.time.{Duration, Instant}
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class ExternalObjFetcher(val providers: ExternalProviders)(using system: ActorSystem):
	import system.dispatcher
	import ExternalObjFetcher.*
	private val log = LoggerFactory.getLogger(getClass)
	private val http = Http()
	private val cache = TrieMap.empty[URI, Future[Cached]]
	private val revalidating = TrieMap.empty[URI, Unit]
	private val poolSettings = ConnectionPoolSettings(system)
		.withMaxConnections(4)
		.withMaxOpenRequests(4096)
		.withUpdatedConnectionSettings(_.withConnectingTimeout(5.seconds).withIdleTimeout(10.seconds))

	def getCitationEager(url: URI, style: CitationStyle): Option[Try[String]] =
		fetchIfNeeded(url).value.map: cachedTry =>
			cachedTry.flatMap: cached =>
				styleField(cached.refs, style) match
					case Some(cit) => Success(cit)
					case None      => Failure(Exception(s"No $style citation in object at $url"))

	def getPidEager(url: URI): Option[String] =
		fetchIfNeeded(url).value.flatMap(_.toOption).flatMap(_.pid)

	private def styleField(refs: References, style: CitationStyle): Option[String] = style match
		case CitationStyle.bibtex => refs.citationBibTex
		case CitationStyle.ris    => refs.citationRis
		case _                    => refs.citationString

	private def fetchIfNeeded(url: URI): Future[Cached] =
		cache.get(url) match
			case None =>
				evictIfFull()
				val fut = doFetch(url)
				cache += url -> fut
				fut
			case Some(fut) =>
				fut.value match
					case Some(Success(cached)) =>
						if isStale(cached.fetchedAt) then revalidate(url)
						fut
					case Some(Failure(_)) =>
						val retry = doFetch(url)
						cache += url -> retry
						retry
					case None => fut

	private def evictIfFull(): Unit =
		if cache.size >= maxEntries then
			val completed = cache.toSeq.collect:
				case (url, fut) if fut.isCompleted => url -> fut.value.flatMap(_.toOption).fold(Instant.MIN)(_.fetchedAt)
			val toEvict = completed.sortBy(_._2).take(cache.size - maxEntries * 3 / 4)
			toEvict.foreach((url, _) => cache.remove(url))

	private def revalidate(url: URI): Unit =
		if revalidating.putIfAbsent(url, ()).isEmpty then
			doFetch(url).onComplete: result =>
				result.foreach(fresh => cache.replace(url, Future.successful(fresh)))
				revalidating.remove(url)

	private def isStale(fetchedAt: Instant): Boolean = Instant.now().isAfter(fetchedAt.plus(ttl))

	private def doFetch(url: URI): Future[Cached] =
		http.singleRequest(
			HttpRequest(uri = url.toString, headers = List(Accept(MediaTypes.`application/json`))),
			settings = poolSettings
		).flatMap: resp =>
			if resp.status.isSuccess() then
				resp.entity.toStrict(10.seconds).flatMap(Unmarshal(_).to[StaticObject])
			else
				resp.discardEntityBytes()
				Future.failed(Exception(s"Got ${resp.status} from $url"))
		.map(obj => Cached(obj.references, obj.pid, Instant.now()))
		.andThen:
			case Failure(err) => log.warn(s"Failed to fetch external object metadata from $url: ${err.getMessage}")

object ExternalObjFetcher:
	private val ttl: Duration = Duration.ofMinutes(5)
	private val maxEntries = 10000
	private case class Cached(refs: References, pid: Option[String], fetchedAt: Instant)
