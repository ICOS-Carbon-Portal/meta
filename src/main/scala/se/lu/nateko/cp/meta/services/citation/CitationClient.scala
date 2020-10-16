package se.lu.nateko.cp.meta.services.citation

import java.util.concurrent.TimeoutException

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

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

trait PlainDoiCiter{
	def getCitationEager(doi: Doi): Option[String]
}

class CitationClient(knownDois: List[Doi], config: CitationConfig)(
	implicit system: ActorSystem, mat: Materializer
) extends PlainDoiCiter{

	private val cache = TrieMap.empty[Doi, Future[String]]

	private val http = Http()
	import system.{dispatcher, scheduler, log}

	if(config.eagerWarmUp) scheduler.scheduleOnce(5.seconds)(warmUpCache())

	def getCitation(doi: Doi): Future[String] = {
		val fut = cache.getOrElseUpdate(doi, fetchTimeLimited(doi))
		fut.failed.foreach{_ =>
			scheduler.scheduleOnce(10.seconds){
				fetchIfNeeded(doi)
			}
		}
		fut
	}

	//not waiting for HTTP; only returns string if the result previously cached
	def getCitationEager(doi: Doi): Option[String] = getCitation(doi).value.flatMap(_.toOption)

	private def fetchTimeLimited(doi: Doi): Future[String] =
		timeLimit(fetchCitation(doi), config.timeoutSec.seconds, scheduler).recoverWith{
			case _: TimeoutException => Future.failed(
				new Exception("Citation formatting service timed out")
			)
		}

	private def fetchIfNeeded(doi: Doi): Future[String] = {

		def recache(): Future[String] = {
			val res = fetchCitation(doi)
			cache += doi -> res
			res
		}

		cache.get(doi).fold(recache()){fut =>
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
				fetchIfNeeded(head).flatMap(_ => warmUp(tail))
		}

		warmUp(knownDois).failed.foreach{_ =>
			scheduler.scheduleOnce(1.hours)(warmUpCache())
		}
	}

	private def fetchCitation(doi: Doi): Future[String] = http.singleRequest(
			HttpRequest(
				//uri = s"https://api.datacite.org/dois/text/x-bibliography/${doi.prefix}/${doi.suffix}?style=${config.style}"
				uri = s"https://citation.crosscite.org/format?doi=${doi.prefix}%2F${doi.suffix}&style=${config.style}&lang=en-US"
			)
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
			case err => errorLite(s"Error fetching citation string from Crosscite: ${err.getMessage}")
		}
		.andThen{
			case Failure(err) => log.warning("Citation fetching error: " + err.getMessage)
			case Success(cit) => log.debug(s"Fetched $cit")
		}

}
