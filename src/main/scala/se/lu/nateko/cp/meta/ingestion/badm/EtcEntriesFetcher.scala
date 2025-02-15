package se.lu.nateko.cp.meta.ingestion.badm

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, RequestEntity, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import spray.json.{JsObject, JsValue}

import scala.concurrent.{ExecutionContext, Future}

object EtcEntriesFetcher {

	def getJson(uri: Uri, payload: JsObject)(implicit system: ActorSystem, m: Materializer): Future[JsObject] = {
		import system.dispatcher
		for(
			entity <- Marshal(payload).to[RequestEntity];
			request = HttpRequest(HttpMethods.POST, uri, Nil, entity);
			resp <- Http().singleRequest(request);
			json <- responseToJson(resp).collect{
					case obj: JsObject => obj
				}
		) yield json
	}

	def responseToJson(resp: HttpResponse)(implicit m: Materializer, ctxt: ExecutionContext): Future[JsValue] = {
		resp.status match {
			case StatusCodes.OK => Unmarshal(resp.entity).to[JsValue]
			case _ =>
				resp.discardEntityBytes()
				Future.failed(new Exception(s"Got ${resp.status} from the ETC metadata server"))
		}
	}
}
