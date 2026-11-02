package se.lu.nateko.cp.meta.services.external

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.{HttpRequest, MediaTypes, StatusCodes}
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.core.data.{References, StaticObject}
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.services.citation.CitationStyle

import java.net.URI
import java.time.{Duration, Instant}
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class ExternalObjFetcher(
	val providers: ExternalProviders,
	ttl: Duration = ExternalObjFetcher.defaultTtl,
	failureTtl: Duration = ExternalObjFetcher.defaultFailureTtl,
	maxEntries: Int = ExternalObjFetcher.defaultMaxEntries
)(using system: ActorSystem):
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
					if Instant.now().isAfter(cached.staleAt) then revalidate(url, cached)
				fut

	private def evictIfFull(): Unit =
		if cache.size >= maxEntries then
			val completed = cache.toSeq.flatMap((url, fut) => fut.value.flatMap(_.toOption).map(url -> _))
			val toEvict = completed
				.sortBy((_, cached) => (cached.result.isSuccess, cached.fetchedAt))
				.take(cache.size - maxEntries * 3 / 4)
			toEvict.foreach((url, _) => cache.remove(url))

	private def revalidate(url: URI, stale: Cached): Unit =
		if revalidating.putIfAbsent(url, ()).isEmpty then
			doFetch(url).onComplete: result =>
				result.foreach(fresh => cache.replace(url, Future.successful(keepOrReplace(stale, fresh))))
				revalidating.remove(url)

	private def keepOrReplace(stale: Cached, fresh: Cached): Cached = fresh.result match
		case Failure(_: RejectedByRemote) => fresh
		case Failure(_) if stale.result.isSuccess => stale.copy(staleAt = Instant.now().plus(failureTtl))
		case _ => fresh

	private def doFetch(url: URI): Future[Cached] =
		http.singleRequest(
			HttpRequest(uri = url.toString, headers = List(Accept(MediaTypes.`application/json`))),
			settings = poolSettings
		).flatMap: resp =>
			if resp.status.isSuccess() then
				resp.entity.toStrict(10.seconds).flatMap(Unmarshal(_).to[StaticObject])
			else
				resp.discardEntityBytes()
				val msg = s"Got ${resp.status} from $url"
				Future.failed(resp.status match
					case _: StatusCodes.ClientError => RejectedByRemote(msg)
					case _ => Exception(msg)
				)
		.map(obj => Fetched(obj.references, obj.pid))
		.andThen:
			case Failure(err) => log.warn(s"Failed to fetch external object metadata from $url: ${err.getMessage}")
		.transform: result =>
			val now = Instant.now()
			Success(Cached(result, now, now.plus(if result.isSuccess then ttl else failureTtl)))

object ExternalObjFetcher:
	val defaultTtl: Duration = Duration.ofMinutes(5)
	val defaultFailureTtl: Duration = Duration.ofSeconds(30)
	val defaultMaxEntries = 10000
	private case class Fetched(refs: References, pid: Option[String])
	private case class Cached(result: Try[Fetched], fetchedAt: Instant, staleAt: Instant)
	private class RejectedByRemote(msg: String) extends Exception(msg)
