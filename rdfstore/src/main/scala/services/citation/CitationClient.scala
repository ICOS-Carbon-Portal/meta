package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.Done
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.doi.DoiMeta
import se.lu.nateko.cp.meta.services.upload.DoiClientFactory
import se.lu.nateko.cp.meta.utils.Mergeable
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.async.errorLite
import se.lu.nateko.cp.meta.utils.async.timeLimit

import java.util.concurrent.TimeoutException
import scala.collection.concurrent.TrieMap
import scala.concurrent._
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NoStackTrace

import CitationClient.*
import akka.event.Logging


enum CitationStyle:
	case HTML, bibtex, ris, TEXT

trait PlainDoiCiter:
	def getCitationEager(doi: Doi, style: CitationStyle): Option[Try[String]]
	def getDoiEager(doi: Doi): Option[Try[DoiMeta]]

trait CitationClient extends PlainDoiCiter:
	protected def citCache: CitationCache = TrieMap.empty
	protected def doiCache: DoiCache = TrieMap.empty

	def getCitation(doi: Doi, citationStyle: CitationStyle): Future[String]
	def getDoiMeta(doi: Doi): Future[DoiMeta]

	//not waiting for HTTP; only returns string if the result previously citCached
	def getCitationEager(doi: Doi, citationStyle: CitationStyle): Option[Try[String]] = getCitation(doi, citationStyle).value
	def getDoiEager(doi: Doi): Option[Try[DoiMeta]] = getDoiMeta(doi).value

	def dropCache(doi: Doi): Unit =
		CitationStyle.values.foreach(style => citCache.remove(doi -> style))
		doiCache.remove(doi)


class CitationClientImpl (
	knownDois: List[Doi], config: CitationClientConfig
)(using system: ActorSystem, mat: Materializer) extends CitationClient:
	import system.{dispatcher, scheduler}
	private val log = Logging.getLogger(system, this)

	override protected val citCache = TrieMap.empty
	override protected val doiCache = TrieMap.empty

	if(config.eagerWarmUp)
		scheduler.scheduleOnce(35.seconds)(warmUpCache(warmupOneCitation, "citation"))
		scheduler.scheduleOnce(15.seconds)(warmUpCache(warmupOneDoiMeta, "metadata"))

	private val http = Http()
	private val doiClientFactory = DoiClientFactory(config.doi)

	def getCitation(doi: Doi, citationStyle: CitationStyle): Future[String] =
		val key = doi -> citationStyle
		withTimeout(fetchIfNeeded(key, citCache, fetchCitation), s"Citation formatting for $doi")

	def getDoiMeta(doi: Doi): Future[DoiMeta] =
		withTimeout(fetchIfNeeded(doi, doiCache, fetchDoiMeta), s"DOI metadata for $doi")

	private def withTimeout[T](fut: Future[T], serviceName: String): Future[T] =
		timeLimit(fut, config.timeoutSec.seconds, scheduler, serviceName).recoverWith:
			case _: TimeoutException =>
				val msg = s"$serviceName service timed out"
				log.warning(msg)
				Future.failed(new Exception(msg) with NoStackTrace)


	private def fetchIfNeeded[K, V](key: K, cache: TrieMap[K, Future[V]], fetchValue: K => Future[V]): Future[V] =
		def recache(): Future[V] = {
			val res = fetchValue(key)
			cache += key -> res
			res
		}

		cache.get(key).fold(recache()){fut =>
			fut.value match
				case Some(Failure(_)) =>
					//if this citation is a completed failure at the moment
					recache()
					fut
				case _ => fut
		}

	def warmupOneCitation(doi: Doi): Future[Validated[Done]] =
		log.debug(s"Warming up citation cache for DOI $doi")

		val allFuts = CitationStyle.values.map{ citStyle =>
			fetchIfNeeded(doi -> citStyle, citCache, fetchCitation).transform{
				case Success(_) => Success(Validated.ok(Done))
				case Failure(err) => Success(Validated.error(err.getMessage))
			}
		}
		Future.reduceLeft(allFuts.toIndexedSeq)(Mergeable.mergeValidated)

	def warmupOneDoiMeta(doi: Doi): Future[Validated[Done]] =
		log.debug(s"Warming up doi meta cache for DOI $doi")

		fetchIfNeeded(doi, doiCache, fetchDoiMeta).transform{
			case Success(_) => Success(Validated.ok(Done))
			case Failure(err) => Success(Validated.error(err.getMessage))
		}

	private def warmUpCache(warmupOne: Doi => Future[Validated[Done]], cacheType: String): Unit =
		val MaxErrors = 5
		def warmUp(dois: List[Doi], soFar: Validated[Done]): Future[Validated[Done]] =
			if soFar.errors.length > MaxErrors then
				val msg = s"Got more than $MaxErrors errors while warming up DOI $cacheType cache, cancelling for now"
				Future.successful(soFar.withExtraError(msg))
			else dois match
				case Nil => Future.successful(soFar)
				case head :: tail =>
					warmupOne(head).flatMap{ first =>
						warmUp(tail, Mergeable.mergeValidated(soFar, first))
					}

		warmUp(knownDois, Validated.ok(Done)).onComplete{
			case Success(v) if v.errors.nonEmpty =>
				log.warning(s"DOI $cacheType cache warmup encountered the following errors (will retry later):\n" +
					v.errors.mkString("\n"))
				scheduler.scheduleOnce(1.hours)(warmUpCache(warmupOne, cacheType))
			case Success(v) =>
				log.info(s"DOI $cacheType cache warmup success")
			case Failure(exception) =>
				log.error(s"DOI $cacheType cache warmup problem", exception)
		}

	private def fetchCitation(key: Key): Future[String] =
		val (doi, style) = key
		http.singleRequest(
			request = HttpRequest(
				uri = style match {
					case CitationStyle.bibtex => s"https://api.datacite.org/dois/application/x-bibtex/${doi.prefix}/${doi.suffix}"
					case CitationStyle.ris    => s"https://api.datacite.org/dois/application/x-research-info-systems/${doi.prefix}/${doi.suffix}"
					case CitationStyle.HTML   => s"https://api.datacite.org/dois/text/x-bibliography/${doi.prefix}/${doi.suffix}?style=${config.style}"
					case CitationStyle.TEXT   => s"https://citation.doi.org/format?doi=${doi.prefix}%2F${doi.suffix}&style=${config.style}&lang=en-US"
				}
			),
			settings = ConnectionPoolSettings(system).withMaxConnections(6).withMaxOpenRequests(10000)
		).flatMap{resp =>
			Unmarshal(resp).to[String].flatMap{payload =>
				if(resp.status.isSuccess) Future.successful(payload)
				//the payload is the error message/page from the citation service
				else errorLite(resp.status.defaultMessage + " " + payload)
			}
		}
		.flatMap{citation =>
			if(citation.trim.isEmpty)
				errorLite("got empty citation text")
			else
				Future.successful(citation.trim)
		}
		.recoverWith{
			case err => errorLite(s"Error fetching citation string for ${key._1} from DataCite: ${err.getMessage}")
		}
		.andThen{
			case Failure(err) => log.warning("Citation fetching error: " + err.getMessage)
			case Success(cit) => log.debug(s"Fetched $cit")
		}

	private def fetchDoiMeta(doi: Doi): Future[DoiMeta] =
		doiClientFactory.client.getMetadata(doi).flatMap{
			case None => Future.failed(new Exception(s"No metadata found for DOI $doi") with NoStackTrace)
			case Some(value) => Future.successful(value)
		}

end CitationClientImpl

object CitationClient:
	type Key = (Doi, CitationStyle)
	type CitationCache = TrieMap[Key, Future[String]]
	type DoiCache = TrieMap[Doi, Future[DoiMeta]]

end CitationClient
