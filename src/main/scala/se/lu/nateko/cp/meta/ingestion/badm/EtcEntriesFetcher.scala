package se.lu.nateko.cp.meta.ingestion.badm

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import spray.json.JsObject
import spray.json.JsValue

object EtcEntriesFetcher {

	def getJson(uri: Uri, payload: JsObject)(implicit system: ActorSystem, m: Materializer): Future[JsObject] = {
		import system.dispatcher

		def responseToJson(resp: HttpResponse): Future[JsObject] = {
			resp.status match {
				case StatusCodes.OK => Unmarshal(resp.entity).to[JsValue].collect{
					case obj: JsObject => obj
				}
				case _ =>
					resp.discardEntityBytes()
					Future.failed(new Exception(s"Got ${resp.status} from the ETC metadata server"))
			}
		}

		for(
			entity <- Marshal(payload).to[RequestEntity];
			request = HttpRequest(HttpMethods.POST, uri, Nil, entity);
			resp <- Http().singleRequest(request);
			json <- responseToJson(resp)
		) yield json
	}
}
