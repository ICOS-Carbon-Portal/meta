package se.lu.nateko.cp.meta.ingestion.badm

import akka.actor.ActorSystem
import akka.stream.Materializer
import scala.concurrent.Future
import spray.json.JsObject
import akka.http.scaladsl.model.Uri

class IcosBadmFetcher(implicit system: ActorSystem, m: Materializer){
	val serviceUrl = "http://www.europe-fluxdata.eu/metadata.aspx/getIcosMetadata"

	def getJson: Future[JsObject] =
		EtcEntriesFetcher.getJson(Uri(serviceUrl), JsObject.empty)

	
}
