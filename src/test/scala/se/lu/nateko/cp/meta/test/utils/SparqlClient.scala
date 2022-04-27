package se.lu.nateko.cp.meta.test.utils

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import scala.concurrent.Future
import se.lu.nateko.cp.meta.core.sparql.SparqlSelectResult
import se.lu.nateko.cp.meta.core.sparql.JsonSupport.given
import java.net.URI
import scala.util.Success
import scala.util.Failure

class SparqlClient(url: URI)(using system: ActorSystem) {
	import system.dispatcher

	private val sparqlJson = MediaType.custom("application/sparql-results+json", binary = false)

	private def httpPost(entity: String): Future[HttpResponse] = {
		Http().singleRequest(
			HttpRequest(
				method = HttpMethods.POST,
				uri = url.toString,
				headers = headers.Accept(MediaTypes.`application/json`, sparqlJson) :: Nil,
				entity = entity
			)
		)
	}

	def select(selectQuery: String): Future[SparqlSelectResult] = {
		httpPost(selectQuery).flatMap{resp =>
			resp.status match {
				case StatusCodes.OK =>
					val entity = resp.entity.contentType.mediaType match {
						case `sparqlJson` =>
							resp.entity.withContentType(ContentTypes.`application/json`)
						case MediaTypes.`application/json` =>
							resp.entity
						case _ =>
							resp.discardEntityBytes()
							throw new Exception(s"Server responded with Content Type ${resp.entity.contentType}")
					}
					Unmarshal(entity).to[SparqlSelectResult]
				case _ =>
					Unmarshal(resp.entity).to[String].transform{
						case Success(errMsg) => Failure(new Exception(errMsg))
						case _ => Failure(new Exception(s"Got ${resp.status} from the server"))
					}
			}
		}
	}

}
