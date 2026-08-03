package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import spray.json.RootJsonFormat

import akka.Done
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.doi.DoiMeta
import se.lu.nateko.cp.doi.core.JsonSupport.{given RootJsonFormat[DoiMeta]}
import se.lu.nateko.cp.doi.core.JsonSupport.{given RootJsonFormat[Doi]}
import se.lu.nateko.cp.meta.CitationConfig
import se.lu.nateko.cp.meta.services.upload.DoiClientFactory
import se.lu.nateko.cp.meta.utils.async.errorLite
import se.lu.nateko.cp.meta.utils.async.timeLimit

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import scala.collection.concurrent.TrieMap
import scala.concurrent._
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NoStackTrace

import CitationClient.*
import akka.event.Logging
import org.slf4j.LoggerFactory


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
	config: CitationConfig, initCitCache: CitationCache, initDoiCache: DoiCache
)(using system: ActorSystem, mat: Materializer) extends CitationClient:
	import system.{dispatcher, scheduler}
	private val log = Logging.getLogger(system, this)

	override protected val citCache = initCitCache
	override protected val doiCache = initDoiCache

	private val http = Http()
	private val doiClientFactory = DoiClientFactory(config.doi)

	// DataCite rate-limits clients (HTTP 429). All outgoing DataCite calls (citation
	// strings and DOI metadata) are funnelled through this single stream, which
	// dispatches at most `dataCiteMaxRequestsPerSecond` task-starts per second.
	// The queue backpressures callers rather than silently dropping requests.
	private val ThrottleBufferSize = 1 << 16
	private val dataCiteThrottle: SourceQueueWithComplete[() => Unit] =
		Source
			.queue[() => Unit](ThrottleBufferSize, OverflowStrategy.backpressure, maxConcurrentOffers = ThrottleBufferSize)
			.throttle(config.dataCiteMaxRequestsPerSecond, 1.second)
			.toMat(Sink.foreach(_()))(Keep.left)
			.run()

	private val pauseUntilNanos = AtomicLong(0)
	private val consecutiveRateLimits = AtomicInteger(0)
	private val MaxRetries = 4
	private val BaseRateLimitBackoff = 30.seconds
	private val MaxRateLimitBackoff = 5.minutes
	private final case class RateLimited(retryAfter: Option[FiniteDuration], details: String)
		extends Exception(details) with NoStackTrace

	private def throttled[T](serviceName: String)(task: => Future[T]): Future[T] =
		val p = Promise[T]()
		val queuedAtNanos = System.nanoTime()
		dataCiteThrottle.offer{() =>
			val waitedMs = (System.nanoTime() - queuedAtNanos) / 1000000
			if waitedMs > 1000 then log.debug(s"DataCite request waited ${waitedMs} ms in the throttle queue before dispatch")
			val pause = (pauseUntilNanos.get - System.nanoTime()).nanos.max(Duration.Zero)
			val afterPause =
				if pause.length == 0 then Future.successful(())
				else akka.pattern.after(pause, scheduler)(Future.successful(()))
			p.completeWith(afterPause.flatMap(_ =>
				timeLimit(task, config.timeoutSec.seconds, scheduler, serviceName)
			))
		}.onComplete:
			case Success(QueueOfferResult.Enqueued) => () // task will run (and complete p) when a rate-limit slot frees up
			case Success(other) =>
				p.tryFailure(new Exception(s"DataCite request was not scheduled (throttle queue: $other)") with NoStackTrace)
			case Failure(err) => p.tryFailure(err)
		p.future

	// Times a single outgoing DataCite call, logs it at debug, and feeds the aggregate
	// progress summary that is emitted at info every `DataCiteLogEveryN` requests.
	private def timedDataCite[T](desc: String)(task: => Future[T]): Future[T] =
		val startNanos = System.nanoTime()
		task.andThen: res =>
			val durMs = (System.nanoTime() - startNanos) / 1000000
			res match
				case Success(_)   => log.debug(s"DataCite OK: $desc in ${durMs} ms")
				case Failure(err) => log.debug(s"DataCite FAILED: $desc in ${durMs} ms: ${err.getMessage}")
			recordDataCite(durMs, res.isSuccess)

	private val DataCiteLogEveryN = 100
	private object dcStats:
		var doneTotal = 0L
		var failedTotal = 0L
		var winCount = 0
		var winFailed = 0
		var winTotalMs = 0L
		var winMinMs = Long.MaxValue
		var winMaxMs = 0L
		var winStartNanos = System.nanoTime()

	private def recordDataCite(durMs: Long, ok: Boolean): Unit = dcStats.synchronized:
		dcStats.doneTotal += 1
		dcStats.winCount += 1
		if !ok then
			dcStats.failedTotal += 1
			dcStats.winFailed += 1
		dcStats.winTotalMs += durMs
		dcStats.winMinMs = math.min(dcStats.winMinMs, durMs)
		dcStats.winMaxMs = math.max(dcStats.winMaxMs, durMs)
		if dcStats.winCount >= DataCiteLogEveryN then
			val elapsedS = (System.nanoTime() - dcStats.winStartNanos) / 1e9
			val rate = if elapsedS > 0 then dcStats.winCount / elapsedS else 0.0
			val avg = dcStats.winTotalMs.toDouble / dcStats.winCount
			log.info(
				f"DataCite progress: ${dcStats.doneTotal} requests done (${dcStats.failedTotal} failed total); " +
				f"last ${dcStats.winCount} (${dcStats.winFailed} failed): avg ${avg}%.0f ms, " +
				f"min ${dcStats.winMinMs} ms, max ${dcStats.winMaxMs} ms, ~${rate}%.1f req/s"
			)
			dcStats.winCount = 0
			dcStats.winFailed = 0
			dcStats.winTotalMs = 0L
			dcStats.winMinMs = Long.MaxValue
			dcStats.winMaxMs = 0L
			dcStats.winStartNanos = System.nanoTime()

	def getCitation(doi: Doi, citationStyle: CitationStyle): Future[String] =
		val key = doi -> citationStyle
		fetchIfNeeded(key, citCache, fetchCitation)

	def getDoiMeta(doi: Doi): Future[DoiMeta] =
		fetchIfNeeded(doi, doiCache, fetchDoiMeta)


	private def fetchIfNeeded[K, V](key: K, cache: TrieMap[K, Future[V]], fetchValue: K => Future[V]): Future[V] =
		def recache(): Future[V] = {
			val res = fetchValue(key)
			cache += key -> res
			res
		}

		cache.get(key).fold(recache()){fut =>
			fut.value match
				case Some(Failure(_)) =>
					// If this is a completed failure, replace it and return the new attempt.
					recache()
				case _ => fut
		}

	private def fetchCitation(key: Key): Future[String] =
		val (doi, style) = key
		val desc = s"$style citation for $doi"
		withRetries(desc):
			throttled(desc):
				timedDataCite(desc):
					val response = http.singleRequest(
						request = HttpRequest(
							uri = style match
								case CitationStyle.bibtex => s"https://api.datacite.org/dois/application/x-bibtex/${doi.prefix}/${doi.suffix}"
								case CitationStyle.ris    => s"https://api.datacite.org/dois/application/x-research-info-systems/${doi.prefix}/${doi.suffix}"
								case CitationStyle.HTML   => s"https://api.datacite.org/dois/text/x-bibliography/${doi.prefix}/${doi.suffix}?style=${config.style}"
								case CitationStyle.TEXT   => s"https://citation.doi.org/format?doi=${doi.prefix}%2F${doi.suffix}&style=${config.style}&lang=en-US",
						),
						settings = ConnectionPoolSettings(system).withMaxConnections(6).withMaxOpenRequests(10000)
					)
					response
						.flatMap(resp =>
							Unmarshal(resp).to[String].flatMap(payload =>
								if resp.status.isSuccess then Future.successful(payload)
								else if resp.status.intValue == 429 then
									Future.failed(RateLimited(retryDelay(resp), s"429 Too Many Requests $payload"))
								else errorLite(s"${resp.status.intValue} ${resp.status.defaultMessage} $payload")
							)
						)
						.flatMap(citation =>
							if citation.trim.isEmpty then errorLite("got empty citation text")
							else Future.successful(citation.trim)
						)
						.recoverWith:
							case err: RateLimited => Future.failed(err)
							case err => errorLite(s"Error fetching citation string for ${key._1} from DataCite: ${err.getMessage}")
						.andThen:
							case Failure(err) => log.warning("Citation fetching error: " + err.getMessage)
							case Success(cit) => log.debug(s"Fetched $cit")

	private def fetchDoiMeta(doi: Doi): Future[DoiMeta] =
		val desc = s"metadata for $doi"
		withRetries(desc):
			throttled(desc):
				timedDataCite(desc):
					doiClientFactory.client.getMetadata(doi).flatMap:
						case None => Future.failed(new Exception(s"No metadata found for DOI $doi") with NoStackTrace)
						case Some(value) => Future.successful(value)

	private def withRetries[T](desc: String, attempt: Int = 0)(task: => Future[T]): Future[T] =
		task.transformWith:
			case success @ Success(_) =>
				consecutiveRateLimits.set(0)
				Future.fromTry(success)
			case Failure(err) if attempt < MaxRetries && transient(err) =>
				val rateLimited = isRateLimited(err)
				val delay =
					if rateLimited then
						err match
							case RateLimited(Some(serverDelay), _) => serverDelay
							case _ =>
								val incidents = consecutiveRateLimits.incrementAndGet()
								math.min(
									BaseRateLimitBackoff.toSeconds * (1L << math.min(incidents - 1, 3)),
									MaxRateLimitBackoff.toSeconds
								).seconds
					else (1L << attempt).seconds
				if rateLimited then
					pauseUntilNanos.accumulateAndGet(
						System.nanoTime() + delay.toNanos,
						(current, proposed) => math.max(current, proposed)
					)
				log.warning(s"DataCite $desc failed (${err.getMessage}); retrying in $delay")
				akka.pattern.after(delay, scheduler)(withRetries(desc, attempt + 1)(task))
			case Failure(err) => Future.failed(err)

	private def transient(err: Throwable): Boolean =
		val msg = Option(err.getMessage).fold("")(_.toLowerCase)
		err.isInstanceOf[RateLimited] || err.isInstanceOf[TimeoutException] ||
			Seq("429", "too many requests", "500", "502", "503", "timeout", "connection", "socket")
				.exists(msg.contains)

	private def isRateLimited(err: Throwable): Boolean =
		val msg = Option(err.getMessage).fold("")(_.toLowerCase)
		err.isInstanceOf[RateLimited] || msg.contains("429") || msg.contains("too many requests")

	private def retryDelay(response: HttpResponse): Option[FiniteDuration] =
		def header(name: String) = response.headers.find(_.is(name)).map(_.value)
		header("retry-after").flatMap(_.toLongOption).map(_.seconds)
			.orElse:
				header("x-ratelimit-reset").flatMap(_.toLongOption).map: resetEpochSeconds =>
					math.max(resetEpochSeconds - System.currentTimeMillis() / 1000, 1).seconds

end CitationClientImpl

object CitationClient:
	import spray.json.*
	import scala.concurrent.ExecutionContext.Implicits.global
	private val log = LoggerFactory.getLogger(getClass())
	type Key = (Doi, CitationStyle)
	type CitationCache = TrieMap[Key, Future[String]]
	type DoiCache = TrieMap[Doi, Future[DoiMeta]]

	val citCacheDumpFile = Paths.get("./citationsCacheDump.json")
	val doiCacheDumpFile = Paths.get("./doiMetaCacheDump.json")


	def readCitCache(): Future[CitationCache] =
		readCache(citCacheDumpFile){cells =>
			val toParse = cells.collect{case JsString(s) => s}
			assert(toParse.length == 3, "Citation dump had an entry with a wrong number of values")
			val doi = Doi.parse(toParse(0)).get
			val style = CitationStyle.valueOf(toParse(1))
			val cit = toParse(2)
			doi -> style -> cit
		}

	def readDoiCache(): Future[DoiCache] =
		readCache(doiCacheDumpFile){cells =>
			assert(cells.length == 2, "Doi dump had an entry with a wrong number of values")
			val doi = cells(0).convertTo[Doi]
			val doiMeta = cells(1).convertTo[DoiMeta]
			doi -> doiMeta
		}

	def writeCitCache(client: CitationClient): Future[Done] =
		writeCache(client.citCache, citCacheDumpFile){case ((doi, style), cit) =>
			JsArray(doi.toJson, JsString(style.toString), JsString(cit))
		}

	def writeDoiCache(client: CitationClient): Future[Done] =
		writeCache(client.doiCache, doiCacheDumpFile)(
			(doi, doiMeta) => JsArray(doi.toJson, doiMeta.toJson)
		)

	private def readCache[K, V](file: Path)(parser: Vector[JsValue] => (K, V)): Future[TrieMap[K, Future[V]]] =
		if !Files.exists(file) then
			log.info(s"No citation cache dump found at $file, starting with an empty cache")
			Future.successful(TrieMap.empty)
		else Future{
			val dump = Files.readString(file).parseJson
			val tuples = dump match
				case JsArray(arrs) => arrs.collect{
					case JsArray(cells) =>
						val (k, v) = parser(cells)
						k -> Future.successful(v)
				}
				case _ => throw Exception("dump was not a JSON array")
			val cache = TrieMap.apply(tuples*)
			log.info(s"Loaded ${cache.size} entries from citation cache dump $file")
			cache
		}.recover{
			case _: NoSuchFileException =>
				log.info(s"Cache dump $file does not exist; starting with empty cache")
				TrieMap.empty
			case err: Throwable =>
				log.warn(s"Could not read citation cache dump at $file ($err), starting with an empty cache")
				TrieMap.empty
		}

	private def writeCache[K, V](
		cache: TrieMap[K, Future[V]], toFile: Path
	)(serializer: (K, V) => JsArray): Future[Done] = Future{
		val arrays = cache.iterator.flatMap{
			case (key, fut) =>
				fut.value.flatMap(_.toOption).map(serializer(key, _))
		}.toVector
		val js = JsArray(arrays).prettyPrint
		import StandardOpenOption.*
		Files.writeString(toFile, js, WRITE, CREATE, TRUNCATE_EXISTING)
		Done
	}

end CitationClient
