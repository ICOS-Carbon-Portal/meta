package se.lu.nateko.cp.meta.services.citation

import java.util.concurrent.TimeoutException

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import se.lu.nateko.cp.meta.utils.async.{timeLimit, errorLite}
import se.lu.nateko.cp.meta.CitationConfig
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import akka.event.LoggingAdapter
import akka.http.scaladsl.settings.ConnectionPoolSettings
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.doi.DoiMeta
import se.lu.nateko.cp.meta.core.data.Envri
import scala.concurrent._
import ExecutionContext.Implicits.global
import se.lu.nateko.cp.doi.core.DoiClient
import se.lu.nateko.cp.doi.core.PlainJavaDoiHttp
import se.lu.nateko.cp.doi.core.DoiClientConfig
import se.lu.nateko.cp.meta.CpmetaConfig
import spray.json.RootJsonFormat
import se.lu.nateko.cp.doi.core.JsonSupport.{given RootJsonFormat[DoiMeta]}
import java.nio.file.Path
import se.lu.nateko.cp.meta.services.upload.DoiServiceClient

enum CitationStyle:
	case HTML, bibtex, ris, TEXT

trait PlainDoiCiter{
	import CitationStyle.*
	def getCitationEager(doi: Doi, style: CitationStyle): Option[Try[String]]
	def getDoiEager(doi: Doi)(using Envri): Option[Try[DoiMeta]]
}

trait CitationClient extends PlainDoiCiter{
	protected def citCache: CitationClient.CitationCache = TrieMap.empty
	protected def doiCache: CitationClient.DoiCache = TrieMap.empty

	def getCitation(doi: Doi, citationStyle: CitationStyle): Future[String]
	def getDoiMeta(doi: Doi)(using Envri): Future[DoiMeta]
	//not waiting for HTTP; only returns string if the result previously citCached
	def getCitationEager(doi: Doi, citationStyle: CitationStyle): Option[Try[String]] = getCitation(doi, citationStyle).value

	def dropCache(doi: Doi): Unit = CitationStyle.values.foreach(style => citCache.remove(doi -> style))

	def getDoiEager(doi: Doi)(using Envri): Option[Try[DoiMeta]] = getDoiMeta(doi).value

}

class CitationClientImpl (
	knownDois: List[Doi], cpMetaConfig: CpmetaConfig, initCitCache: CitationClient.CitationCache, initDoiCache: CitationClient.DoiCache
)(using system: ActorSystem, mat: Materializer, envri: Envri) extends CitationClient{
	import CitationStyle.*
	import CitationClient.Key

	private val config = cpMetaConfig.citations

	val doiClient = new DoiServiceClient(cpMetaConfig).getClient

	override protected val citCache = initCitCache
	override protected val doiCache = initDoiCache

	private val http = Http()
	import system.{dispatcher, scheduler, log}
	import CitationStyle.*

	def getDoiMeta(doi: Doi)(using Envri): Future[DoiMeta] =
		timeLimit(fetchIfNeeded[Doi, DoiMeta](doi, doiCache, fetchDoiMeta), config.timeoutSec.seconds, scheduler).recoverWith{
			case _: TimeoutException => Future.failed(
				new Exception("Doi meta formatting service timed out")
			)
		}

	def fetchDoiMeta(doi: Doi)(using Envri): Future[DoiMeta] = doiClient.getMetadata(doi).flatMap{resp =>
		resp match {
			case None => Future.failed(new Error)
			case Some(value) => Future.successful(value)
		}
	}

	if(config.eagerWarmUp) scheduler.scheduleOnce(35.seconds)(warmUpCache())

	def getCitation(doi: Doi, citationStyle: CitationStyle): Future[String] = {
		val key = doi -> citationStyle
		timeLimit(fetchIfNeeded[Key, String](key, citCache, fetchCitation), config.timeoutSec.seconds, scheduler).recoverWith{
			case _: TimeoutException => Future.failed(
				new Exception("Citation formatting service timed out")
			)
		}
	}

	private def fetchIfNeeded[K, V](key: K, cache: TrieMap[K, Future[V]], fetchValue: K => Future[V]): Future[V] = {

		def recache(): Future[V] = {
			val res = fetchValue(key)
			cache += key -> res
			res
		}

		cache.get(key).fold(recache()){fut =>
			fut.value match{
				case Some(Failure(_)) =>
					//if this citation is a completed failure at the moment
					recache()
				case _ => fut
			}
		}
	}

	private def warmUpCache(): Unit = {

		def warmUp(dois: List[Doi]): Future[Done] = dois match {
			case Nil => Future.successful(Done)
			case head :: tail =>
				Future.sequence(
					CitationStyle.values.toSeq.map{citStyle => fetchIfNeeded[Key, String](head -> citStyle, citCache, fetchCitation)}
				).flatMap(_ => warmUp(tail))
		}

		warmUp(knownDois).failed.foreach{_ =>
			scheduler.scheduleOnce(1.hours)(warmUpCache())
		}
	}

	private def fetchCitation(key: Key): Future[String] =
		val (doi, style) = key
		http.singleRequest(
			request = HttpRequest(
				uri = style match {
					case CitationStyle.bibtex => s"https://api.datacite.org/dois/application/x-bibtex/${doi.prefix}/${doi.suffix}"
					case CitationStyle.ris    => s"https://api.datacite.org/dois/application/x-research-info-systems/${doi.prefix}/${doi.suffix}"
					case CitationStyle.HTML   => s"https://api.datacite.org/dois/text/x-bibliography/${doi.prefix}/${doi.suffix}?style=${config.style}"
					case CitationStyle.TEXT   => s"https://citation.crosscite.org/format?doi=${doi.prefix}%2F${doi.suffix}&style=${config.style}&lang=en-US"
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

object CitationClient{
	import spray.json.*
	import scala.concurrent.ExecutionContext.Implicits.global
	type Key = (Doi, CitationStyle)
	type CitationCache = TrieMap[Key, Future[String]]
	type DoiCache = TrieMap[Doi, Future[DoiMeta]]

	opaque type Dump = JsValue

	val citCacheDumpFile = Paths.get("./citationsCacheDump.json")
	val doiCacheDumpFile = Paths.get("./doiMetaCacheDump.json")

	def dumpDoiCache(client: CitationClient): Dump = {
		val arrays = client.doiCache.iterator.flatMap{
			case ((doi), fut) =>
				fut.value.flatMap(_.toOption).map{doiMeta =>
					val strs = Vector(JsString(doi.toString), doiMeta.toJson)
					JsArray(strs)
				}
		}.toVector
		JsArray(arrays)
	}

	def dumpCitCache(client: CitationClient): Dump = {
		val arrays = client.citCache.iterator.flatMap{
			case ((doi, style), fut) =>
				fut.value.flatMap(_.toOption).map{cit =>
					val strs = Vector(doi.toString, style.toString, cit)
					JsArray(strs.map(JsString.apply))
				}
		}.toVector
		JsArray(arrays)
	}

	def reconstructDoiCache(cells: Vector[JsValue]): (Doi, Future[DoiMeta]) =
		assert(cells.length == 2, "Doi dump had an entry with a wrong number of values")
		val toParse = cells.collect{case JsString(s) => s}
		val doi = Doi.parse(toParse(0)).get
		val fut = Future.successful(cells(1).toString.parseJson.convertTo[DoiMeta])
		doi -> fut

	def reconstructCitCache(cells: Vector[JsValue]): (Key, Future[String]) =
		val toParse = cells.collect{case JsString(s) => s}
		assert(toParse.length == 3, "Citation dump had an entry with a wrong number of values")
		val doi = Doi.parse(toParse(0)).get
		val style = CitationStyle.valueOf(toParse(1))
		val fut = Future.successful(toParse(2))
		doi -> style -> fut

	def saveCache(client: CitationClient): Future[Done] = Future{
		import StandardOpenOption.*
		Files.writeString(citCacheDumpFile, dumpCitCache(client).prettyPrint, WRITE, CREATE, TRUNCATE_EXISTING)
		Files.writeString(doiCacheDumpFile, dumpDoiCache(client).prettyPrint, WRITE, CREATE, TRUNCATE_EXISTING)
		Done
	}

	private def readCache[K, V](log: LoggingAdapter, file: Path, parser: Vector[JsValue] => (K, V)): Future[TrieMap[K, V]] =
		Future{
			val dump = Files.readString(file).parseJson
			val tuples = dump match
				case JsArray(arrs) => arrs.collect{
					case JsArray(cells) =>
						parser(cells)
				}
				case _ => throw Exception("Citation/DOI dump was not a JSON array")
			TrieMap.apply(tuples*)
		}.recover{
			case err: Throwable =>
				log.error(err, "Could not read cache dump")
				TrieMap.empty
		}

	def readDoiCache(log: LoggingAdapter): Future[DoiCache] =
		readCache(log, doiCacheDumpFile, reconstructDoiCache)

	def readCitCache(log: LoggingAdapter): Future[CitationCache] =
		readCache(log, citCacheDumpFile, reconstructCitCache)
}
