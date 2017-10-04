package se.lu.nateko.cp.meta.services.upload.etc

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import se.lu.nateko.cp.meta.core.etcupload.DataType
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.ingestion.badm.EtcEntriesFetcher
import se.lu.nateko.cp.meta.ingestion.badm.Parser
import spray.json.JsNumber
import spray.json.JsObject

class EtcFileMetadataProvider(implicit system: ActorSystem, m: Materializer) extends EtcFileMetadataStore{

	import system.dispatcher

	private[this] var inner: Option[EtcFileMetadataStore] = None
	private[this] var retryCount: Int = 0

	def lookupFile(station: StationId, loggerId: Int, fileId: Int, dataType: DataType.Value) =
		inner.flatMap(_.lookupFile(station, loggerId, fileId, dataType))

	def lookupLogger(station: StationId, loggerId: Int) =
		inner.flatMap(_.lookupLogger(station, loggerId))

	def getUtcOffset(station: StationId) =
		inner.flatMap(_.getUtcOffset(station))

	system.scheduler.schedule(Duration.Zero, 5.hours)(fetchFromEtc())

	private def fetchFromEtc(): Unit = EtcEntriesFetcher
		.getJson(
			Uri("http://www.europe-fluxdata.eu/metadata.aspx/getIcosMetadata"),
			JsObject("site" -> JsObject("ID" -> JsNumber(70)))
		)
		.map(json => EtcFileMetadataStore(Parser.parseEntriesFromEtcJson(json)))
		.onComplete{

			case Success(store) =>
				inner = Some(store)
				system.log.info("Successfully fetched logger/file/station metadata from ETC")
				retryCount = 0

			case Failure(err) =>
				system.log.error(err, "Problem fetching/parsing metadata from ETC")
				if(retryCount < 3){
					system.scheduler.scheduleOnce(10.minutes){
						retryCount += 1
						fetchFromEtc()
					}
				}
		}
}
