package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import spray.json.RootJsonFormat

import akka.Done
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
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
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
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
	knownDois: List[Doi], config: CitationConfig, initCitCache: CitationCache, initDoiCache: DoiCache
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
	// Requests beyond the buffer are dropped and re-attempted on the next access/materialization.
	private val ThrottleBufferSize = 1 << 16
	private val dataCiteThrottle: SourceQueueWithComplete[() => Unit] =
		Source
			.queue[() => Unit](ThrottleBufferSize, OverflowStrategy.dropNew, maxConcurrentOffers = ThrottleBufferSize)
			.throttle(config.dataCiteMaxRequestsPerSecond, 1.second)
			.toMat(Sink.foreach(_()))(Keep.left)
			.run()

	private def throttled[T](task: => Future[T]): Future[T] =
		val p = Promise[T]()
		val queuedAtNanos = System.nanoTime()
		dataCiteThrottle.offer{() =>
			val waitedMs = (System.nanoTime() - queuedAtNanos) / 1000000
			if waitedMs > 1000 then log.debug(s"DataCite request waited ${waitedMs} ms in the throttle queue before dispatch")
			p.completeWith(task)
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

	private def fetchCitation(key: Key): Future[String] = throttled:
		val (doi, style) = key
		timedDataCite(s"$style citation for $doi"){
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
		}

	private def fetchDoiMeta(doi: Doi): Future[DoiMeta] = throttled:
		timedDataCite(s"metadata for $doi"){
			doiClientFactory.client.getMetadata(doi).flatMap{
				case None => Future.failed(new Exception(s"No metadata found for DOI $doi") with NoStackTrace)
				case Some(value) => Future.successful(value)
			}
		}

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
