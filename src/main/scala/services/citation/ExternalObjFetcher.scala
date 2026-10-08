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
		fetchedEager(url).map: fetchedTry =>
			fetchedTry.flatMap: fetched =>
				styleField(fetched.refs, style) match
					case Some(cit) => Success(cit)
					case None      => Failure(Exception(s"No $style citation in object at $url"))

	def getPidEager(url: URI): Option[String] =
		fetchedEager(url).flatMap(_.toOption).flatMap(_.pid)

	private def fetchedEager(url: URI): Option[Try[Fetched]] =
		fetchIfNeeded(url).value.map(_.flatMap(_.result))

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
				fut.value.flatMap(_.toOption).foreach: cached =>
					if cached.isStale then revalidate(url)
				fut

	private def evictIfFull(): Unit =
		if cache.size >= maxEntries then
			val completed = cache.toSeq.flatMap((url, fut) => fut.value.flatMap(_.toOption).map(url -> _))
			val toEvict = completed
				.sortBy((_, cached) => (cached.result.isSuccess, cached.fetchedAt))
				.take(cache.size - maxEntries * 3 / 4)
			toEvict.foreach((url, _) => cache.remove(url))

	private def revalidate(url: URI): Unit =
		if revalidating.putIfAbsent(url, ()).isEmpty then
			doFetch(url).onComplete: result =>
				result.foreach(fresh => cache.replace(url, Future.successful(fresh)))
				revalidating.remove(url)

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
		.map(obj => Fetched(obj.references, obj.pid))
		.andThen:
			case Failure(err) => log.warn(s"Failed to fetch external object metadata from $url: ${err.getMessage}")
		.transform(result => Success(Cached(result, Instant.now())))

object ExternalObjFetcher:
	private val ttl: Duration = Duration.ofMinutes(5)
	private val failureTtl: Duration = Duration.ofSeconds(30)
	private val maxEntries = 10000
	private case class Fetched(refs: References, pid: Option[String])
	private case class Cached(result: Try[Fetched], fetchedAt: Instant):
		def isStale: Boolean =
			Instant.now().isAfter(fetchedAt.plus(if result.isSuccess then ttl else failureTtl))
