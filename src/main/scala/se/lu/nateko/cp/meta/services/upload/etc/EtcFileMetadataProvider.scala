package se.lu.nateko.cp.meta.services.upload.etc

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import akka.actor.ActorSystem
import akka.stream.Materializer
import se.lu.nateko.cp.meta.core.etcupload.DataType
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.ingestion.badm.EtcEntriesFetcher
import se.lu.nateko.cp.meta.ingestion.badm.Parser
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import se.lu.nateko.cp.meta.EtcUploadConfig

class EtcFileMetadataProvider(conf: EtcUploadConfig)(implicit system: ActorSystem, m: Materializer) extends EtcFileMetadataStore{

	import system.dispatcher

	private[this] var inner: Option[EtcFileMetadataStore] = None
	private[this] var retryCount: Int = 0

	def lookupFile(station: StationId, loggerId: Int, fileId: Int, dataType: DataType.Value) =
		inner.flatMap(_.lookupFile(station, loggerId, fileId, dataType))

	def lookupLogger(station: StationId, loggerId: Int) =
		inner.flatMap(_.lookupLogger(station, loggerId))

	def getUtcOffset(station: StationId) =
		inner.flatMap(_.getUtcOffset(station))

	private val fetchInterval = 5.hours
	private val initDelay = if(conf.ingestFileMetaAtStart) Duration.Zero else fetchInterval
	system.scheduler.schedule(initDelay, fetchInterval)(fetchFromEtc())

	private def fetchFromEtc(): Unit = Http()
		.singleRequest(HttpRequest(uri = conf.fileMetaService.toASCIIString))
		.flatMap(EtcEntriesFetcher.responseToJson)
		.map(json => EtcFileMetadataStore(Parser.parseEntriesFromEtcJson(json)))
		.onComplete{

			case Success(store) =>
				inner = Some(store)
				system.log.info(s"Fetched ETC logger/file metadata from ${conf.fileMetaService}")
				retryCount = 0

			case Failure(err) =>
				system.log.error(err, "Problem fetching/parsing ETC logger/file metadata")
				if(retryCount < 3){
					system.scheduler.scheduleOnce(10.minutes){
						retryCount += 1
						fetchFromEtc()
					}
				}
		}
}
