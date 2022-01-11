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

case class Doi private(val prefix: String, val suffix: String)

object Doi{
	def unapply(s: String): Option[Doi] = s.split('/').toList match{
		case prefix :: suffix :: Nil => Some(Doi(prefix, suffix))
		case _ => None
	}
}

object CitationStyle extends Enumeration{
	type CitationStyle = Value
	val TEXT   = Value
	val BIBTEX = Value("bibtex")
	val RIS    = Value("ris")
}

trait PlainDoiCiter{
	import CitationStyle._
	def getCitationEager(doi: Doi, style: CitationStyle): Option[Try[String]]
}

class CitationClient(knownDois: List[Doi], config: CitationConfig)(
	implicit system: ActorSystem, mat: Materializer
) extends PlainDoiCiter{
	import CitationStyle._

	private type Key = (Doi, CitationStyle)
	private val cache = TrieMap.empty[Key, Future[String]]

	private val http = Http()
	import system.{dispatcher, scheduler, log}
	import CitationStyle._

	if(config.eagerWarmUp) scheduler.scheduleOnce(5.seconds)(warmUpCache())

	def getCitation(doi: Doi, citationStyle: CitationStyle): Future[String] = {
		val key = doi -> citationStyle
		val fut = cache.getOrElseUpdate(key, fetchTimeLimited(key))
		fut.failed.foreach{_ =>
			scheduler.scheduleOnce(10.seconds){
				fetchIfNeeded(key)
			}
		}
		fut
	}

	//not waiting for HTTP; only returns string if the result previously cached
	def getCitationEager(doi: Doi, citationStyle: CitationStyle): Option[Try[String]] = getCitation(doi, citationStyle).value

	private def fetchTimeLimited(key: Key): Future[String] =
		timeLimit(fetchCitation(key), config.timeoutSec.seconds, scheduler).recoverWith{
			case _: TimeoutException => Future.failed(
				new Exception("Citation formatting service timed out")
			)
		}

	private def fetchIfNeeded(key: Key): Future[String] = {

		def recache(): Future[String] = {
			val res = fetchCitation(key)
			cache += key -> res
			res
		}

		cache.get(key).map{fut =>
			fut.value match{
				case Some(Failure(_)) =>
					//if this citation is a completed failure at the moment
					recache()
				case _ => fut
			}
		}.getOrElse(recache())
	}

	private def warmUpCache(): Unit = {

		def warmUp(dois: List[Doi]): Future[Done] = dois match {
			case Nil => Future.successful(Done)
			case head :: tail =>
				Future.sequence(
					CitationStyle.values.toSeq.map{citStyle => fetchIfNeeded(head -> citStyle)}
				).flatMap(_ => warmUp(tail))
		}

		warmUp(knownDois).failed.foreach{_ =>
			scheduler.scheduleOnce(1.hours)(warmUpCache())
		}
	}

	private def fetchCitation(key: Key): Future[String] = http.singleRequest{
			val (doi, style) = key
			HttpRequest(
				uri = style match {
					case CitationStyle.BIBTEX => s"https://api.datacite.org/dois/application/x-bibtex/${doi.prefix}/${doi.suffix}"
					case CitationStyle.RIS =>    s"https://api.datacite.org/dois/application/x-research-info-systems/${doi.prefix}/${doi.suffix}"
					case _ =>                    s"https://api.datacite.org/dois/text/x-bibliography/${doi.prefix}/${doi.suffix}?style=${config.style}"
				}
			)
		}.flatMap{resp =>
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
