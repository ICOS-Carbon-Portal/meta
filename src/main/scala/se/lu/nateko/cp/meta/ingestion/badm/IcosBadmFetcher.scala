package se.lu.nateko.cp.meta.ingestion.badm

import spray.json.JsObject

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import scala.concurrent.Future

class IcosBadmFetcher(implicit system: ActorSystem, m: Materializer){
	val serviceUrl = "http://www.europe-fluxdata.eu/metadata.aspx/getIcosMetadata"

	def getJson: Future[JsObject] =
		EtcEntriesFetcher.getJson(Uri(serviceUrl), JsObject.empty)

	
}
