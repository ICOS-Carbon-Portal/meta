package se.lu.nateko.cp.meta.api

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Success

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaRange
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer

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

	system.scheduler.scheduleOnce(2.seconds)(warmUpCache())

	def getCitation(doi: Doi): Future[String] = cache
		.getOrElseUpdate(doi, fetchCitation(doi))
		.recoverWith{
			case _: Throwable =>
				fetchCitation(doi).andThen{
					case Success(citation) =>
						cache += doi -> Future.successful(citation)
				}
		}

	private def warmUpCache(): Unit = {

		def warmUp(dois: List[Doi]): Future[Done] = dois match {
			case Nil => Future.successful(Done)
			case head :: tail =>
				getCitation(head).flatMap{_ => warmUp(tail)}
		}

		warmUp(knownDois).failed.foreach{_ =>
			system.scheduler.scheduleOnce(1.hours)(warmUpCache())
		}
	}

	private def fetchCitation(doi: Doi): Future[String] = requestRedirect(doi)
		.flatMap{
			_.header[Location].fold{
				Future.failed[Location](
					new Exception("Error getting citation from DataCite (no Location header in response).")
				)
			}{
				Future.successful
			}
		}
		.flatMap(requestCitation)
		.flatMap(Unmarshal(_).to[String])
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
