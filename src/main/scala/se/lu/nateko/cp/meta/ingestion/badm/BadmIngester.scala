package se.lu.nateko.cp.meta.ingestion.badm

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import se.lu.nateko.cp.meta.ingestion.Ingester
import spray.json.JsObject


class BadmIngester(implicit system: ActorSystem, m: Materializer){
	private[this] val urlPrefix = "https://static.icos-cp.eu/share/metadata/badm/"

	val variablesUrl = urlPrefix + "variablesHarmonized_OTC_CP.csv"
	val badmVocabsUrl = urlPrefix + "variablesHarmonizedVocab_OTC_CP.csv"
	val etcBadmServerUrl = "http://www.europe-fluxdata.eu/metadata.aspx/getMetaAll"
	//val badmEntriesUrl = urlPrefix + "AncillaryCP_117_20160321.csv"

	private val http = Http()
	import system.dispatcher

	private def getUrl(url: String): Future[String] = http
		.singleRequest(HttpRequest(uri = url))
		.flatMap(_.entity.dataBytes.runReduce(_ ++ _))
		.map(_.utf8String)


	def getSchemaAndValuesIngesters: (Ingester, Ingester) = {
		lazy val schema = for(
			variablesCsv <- getUrl(variablesUrl);
			vocabsCsv <- getUrl(badmVocabsUrl)
		) yield BadmSchema.parseSchemaFromCsv(variablesCsv, vocabsCsv)

		//val badmEntries = getUrl(badmEntriesUrl).map(Parser.parseEntriesFromCsv)
		lazy val badmEntries = getEtcBadmEntriesJson.map(Parser.parseEntriesFromEtcJson)
			.recoverWith{
				case err : Throwable =>
					system.log.error(err, "Failed importing BADM metadata from ETC's web service")
				Future.successful(Seq.empty)
			}

		(new RdfBadmSchemaIngester(schema), new RdfBadmEntriesIngester(badmEntries, schema))
	}

	private def getEtcBadmEntriesJson: Future[JsObject] =
		EtcEntriesFetcher.getJson(Uri(etcBadmServerUrl), JsObject.empty)
}
