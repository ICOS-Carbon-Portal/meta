package se.lu.nateko.cp.meta.ingestion.badm

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import se.lu.nateko.cp.meta.ingestion.Ingester
import spray.json.JsObject
import se.lu.nateko.cp.meta.core.MetaCoreConfig.EnvriConfigs
import scala.util.Success
import scala.util.Failure


class BadmIngester(implicit system: ActorSystem, m: Materializer, envriConfs: EnvriConfigs){
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
		lazy val schema = getUrl(variablesUrl).zip(getUrl(badmVocabsUrl)).map{
			case (variablesCsv, vocabsCsv) => BadmSchema.parseSchemaFromCsv(variablesCsv, vocabsCsv)
		}.andThen{
			case Failure(err) =>
				system.log.error(err, s"Failed importing BADM schema from $urlPrefix")
		}

		//val badmEntries = getUrl(badmEntriesUrl).map(Parser.parseEntriesFromCsv)
		lazy val badmEntries = getEtcBadmEntriesJson.map(Parser.parseEntriesFromEtcJson)
			.andThen{
				case Success(entries) =>
					system.log.info(s"Fetched ${entries.size} BADM entries from $etcBadmServerUrl")
				case Failure(err) =>
					system.log.error(err, "Failed importing BADM metadata from ETC's web service")
			}
			.fallbackTo(Future.successful(Seq.empty))

		(new RdfBadmSchemaIngester(schema), new RdfBadmEntriesIngester(badmEntries, schema))
	}

	private def getEtcBadmEntriesJson: Future[JsObject] =
		EtcEntriesFetcher.getJson(Uri(etcBadmServerUrl), JsObject.empty)
}
