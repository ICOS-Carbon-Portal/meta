package se.lu.nateko.cp.meta.api

import akka.actor.ActorSystem
import scala.concurrent.Future
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaRange
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model.MediaType
import scala.collection.concurrent.TrieMap
import scala.util.Success
import akka.stream.Materializer
import scala.concurrent.duration.DurationInt

case class Doi private(val prefix: String, val suffix: String)
object Doi{
	def unapply(s: String): Option[Doi] = s.split('/').toList match{
		case prefix :: suffix :: Nil => Some(Doi(prefix, suffix))
		case _ => None
	}
}

class CitationClient(knownDois: List[Doi])(implicit system: ActorSystem, mat: Materializer) {

	private val cache = TrieMap.empty[Doi, Future[String]]

	private val http = Http()
	import system.dispatcher

	system.scheduler.scheduleOnce(2.seconds)(warmUpCache(knownDois))

	def getCitation(doi: Doi): Future[String] = cache
		.getOrElseUpdate(doi, fetchCitation(doi))
		.recoverWith{
			case _: Throwable =>
				fetchCitation(doi).andThen{
					case Success(citation) =>
						cache += doi -> Future.successful(citation)
				}
		}

	private def warmUpCache(dois: List[Doi]): Unit = dois match {
		case Nil => ()
		case head :: tail =>
			getCitation(head).foreach{_ => warmUpCache(tail)}
	}

	private def fetchCitation(doi: Doi): Future[String] = requestRedirect(doi)
		.flatMap{resp =>

			resp.header[Location].map{

				requestCitation(_).flatMap(Unmarshal(_).to[String])

			}.getOrElse(
				Future.failed(
					new Exception("Error getting citation from DataCite (no Location header in response).")
				)
			)
		}
		.flatMap{citation =>
			if(citation.trim.isEmpty)
				Future.failed(new Exception("Got empty citation text from DataCite"))
			else
				Future.successful(citation)
		}

	private val acceptBiblioRange = Accept(
		MediaRange(MediaType.text("x-bibliography")).withParams(Map("style" -> "apa"))
	)

	private def request(uri: Uri) = http.singleRequest{
		HttpRequest(uri = uri, headers = acceptBiblioRange :: Nil)
	}

	private def requestRedirect(doi: Doi) = request(s"https://doi.org/${doi.prefix}/${doi.suffix}")
	private def requestCitation(location: Location) = request(location.uri)
}
