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
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class StaticObjCitationFetcher(using system: ActorSystem):
	import system.dispatcher
	private val log = LoggerFactory.getLogger(getClass)
	private val http = Http()
	private val cache = TrieMap.empty[URI, Future[References]]

	def getCitationEager(url: URI, style: CitationStyle): Option[Try[String]] =
		fetchIfNeeded(url).value.map: refsTry =>
			refsTry.flatMap: refs =>
				styleField(refs, style) match
					case Some(cit) => Success(cit)
					case None      => Failure(Exception(s"No $style citation in object at $url"))

	private def styleField(refs: References, style: CitationStyle): Option[String] = style match
		case CitationStyle.bibtex => refs.citationBibTex
		case CitationStyle.ris    => refs.citationRis
		case _                    => refs.citationString

	private def fetchIfNeeded(url: URI): Future[References] =
		def recache() =
			val fut = doFetch(url)
			cache += url -> fut
			fut
		cache.get(url).fold(recache()): fut =>
			fut.value match
				case Some(Failure(_)) => recache(); fut
				case _ => fut

	private def doFetch(url: URI): Future[References] =
		http.singleRequest(HttpRequest(
			uri = url.toString,
			headers = List(Accept(MediaTypes.`application/json`))
		)).flatMap: resp =>
			Unmarshal(resp).to[StaticObject]
		.map(_.references)
		.andThen:
			case Failure(err) => log.warn(s"Failed to fetch citation from $url: ${err.getMessage}")
