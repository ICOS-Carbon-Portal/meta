package se.lu.nateko.cp.meta.ingestion.badm

import java.net.URL

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.sprayJsValueUnmarshaller
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaRange.apply
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.util.ByteString
import se.lu.nateko.cp.meta.ingestion.Ingester
import spray.json.JsObject
import spray.json.JsValue


object BadmIngester{
	private[this] val urlPrefix = "https://static.icos-cp.eu/share/metadata/badm/"

	val variablesUrl = new URL(urlPrefix + "variablesHarmonized_OTC_CP.csv")
	val badmVocabsUrl = new URL(urlPrefix + "variablesHarmonizedVocab_OTC_CP.csv")
	val etcBadmServerUrl = "http://www.europe-fluxdata.eu/metadata.aspx/getMetaAll"
	//val badmEntriesUrl = new URL(urlPrefix + "AncillaryCP_117_20160321.csv")


	def getSchemaAndValuesIngesters(implicit system: ActorSystem, m: Materializer): (Ingester, Ingester) = {
		val schema = BadmSchema.parseSchemaFromCsv(variablesUrl.openStream(), badmVocabsUrl.openStream())

		//val badmEntries = Parser.parseEntriesFromCsv(badmEntriesUrl.openStream())
		val badmEntries = try{
			import system.dispatcher
			val badmEntriesFut = getEtcBadmEntriesJson.map(Parser.parseEntriesFromEtcJson)
			Await.result(badmEntriesFut, 10 seconds)
		}catch{
			case err : Throwable =>
				system.log.error(err, "Failed importing BADM metadata from ETC's web service")
				Seq.empty
		}

		(new RdfBadmSchemaIngester(schema), new RdfBadmEntriesIngester(badmEntries, schema))
	}

	private def getEtcBadmEntriesJson(implicit system: ActorSystem, m: Materializer): Future[JsObject] = {
		import system.dispatcher
		val request = HttpRequest(
			HttpMethods.POST,
			Uri(etcBadmServerUrl),
			Nil,
			//Accept(MediaTypes.`application/json`) :: Nil,
			HttpEntity.Strict(ContentTypes.`application/json`, ByteString.empty)
		)
		Http().singleRequest(request).flatMap(
			resp => resp.status match {
				case StatusCodes.OK => Unmarshal(resp.entity).to[JsValue].collect{
					case obj: JsObject => obj
				}
				case _ => Future.failed(new Exception(s"Got ${resp.status} from the ETC metadata server"))
			}
		)
	}
}
