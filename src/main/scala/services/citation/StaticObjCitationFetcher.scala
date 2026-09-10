package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.{HttpRequest, MediaTypes}
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.core.data.{References, StaticObject}
import se.lu.nateko.cp.meta.core.data.JsonSupport.given

import java.net.URI
import java.time.{Duration, Instant}
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class StaticObjCitationFetcher(using system: ActorSystem):
	import system.dispatcher
	import StaticObjCitationFetcher.*
	private val log = LoggerFactory.getLogger(getClass)
	private val http = Http()
	private val cache = TrieMap.empty[URI, Future[Cached]]
	private val revalidating = TrieMap.empty[URI, Unit]

	def getCitationEager(url: URI, style: CitationStyle): Option[Try[String]] =
		fetchIfNeeded(url).value.map: cachedTry =>
			cachedTry.flatMap: cached =>
				styleField(cached.refs, style) match
					case Some(cit) => Success(cit)
					case None      => Failure(Exception(s"No $style citation in object at $url"))

	private def styleField(refs: References, style: CitationStyle): Option[String] = style match
		case CitationStyle.bibtex => refs.citationBibTex
		case CitationStyle.ris    => refs.citationRis
		case _                    => refs.citationString

	private def fetchIfNeeded(url: URI): Future[Cached] =
		cache.get(url) match
			case None =>
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

	private def revalidate(url: URI): Unit =
		if revalidating.putIfAbsent(url, ()).isEmpty then
			doFetch(url).onComplete: result =>
				result.foreach(fresh => cache += (url -> Future.successful(fresh)))
				revalidating.remove(url)

	private def isStale(fetchedAt: Instant): Boolean = Instant.now().isAfter(fetchedAt.plus(ttl))

	private def doFetch(url: URI): Future[Cached] =
		http.singleRequest(HttpRequest(
			uri = url.toString,
			headers = List(Accept(MediaTypes.`application/json`))
		)).flatMap: resp =>
			Unmarshal(resp).to[StaticObject]
		.map(obj => Cached(obj.references, Instant.now()))
		.andThen:
			case Failure(err) => log.warn(s"Failed to fetch citation from $url: ${err.getMessage}")

object StaticObjCitationFetcher:
	private val ttl: Duration = Duration.ofMinutes(5)
	private case class Cached(refs: References, fetchedAt: Instant)
