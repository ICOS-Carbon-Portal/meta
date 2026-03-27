package se.lu.nateko.cp.meta.services.upload.etc

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.Materializer
import se.lu.nateko.cp.meta.EtcConfig
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.metaflow.icos.EtcMetaSource
import se.lu.nateko.cp.meta.services.CpVocab

import scala.concurrent.duration.*
import scala.util.{Failure, Success}

class EtcFileMetadataProvider(conf: EtcConfig, vocab: CpVocab)(using system: ActorSystem) extends EtcFileMetadataStore{

	import system.dispatcher

	private val log = Logging.getLogger(system, this)
	private val metaSrc = new EtcMetaSource(conf, vocab)
	private var inner: Option[EtcFileMetadataStore] = None
	private var retryCount: Int = 0

	def lookupFile(key: EtcFileMetaKey) = inner.flatMap(_.lookupFile(key))

	def getUtcOffset(station: StationId) = inner.flatMap(_.getUtcOffset(station))

	def stationTcId(station: StationId) = inner.flatMap(_.stationTcId(station))

	if conf.ingestFileMeta then
		system.scheduler.scheduleWithFixedDelay(Duration.Zero, 5.hours)(() => fetchFromEtc())

	private def fetchFromEtc(): Unit = metaSrc.getFileMeta.onComplete{

			case Success(storeV) =>
				storeV.errors.foreach{err =>
					log.warning("ETC logger/file metadata problem: " + err)
				}
				storeV.result.fold{
					log.error("ETC logger/file metadata was not (re-)initialized")
				}{store =>
					inner = Some(store)
					log.info(s"Fetched ETC logger/file metadata from ${conf.metaService}")
					retryCount = 0
				}

			case Failure(err) =>
				log.error(err, "Problem fetching/parsing ETC logger/file metadata")
				if(retryCount < 3){
					system.scheduler.scheduleOnce(10.minutes){
						retryCount += 1
						fetchFromEtc()
					}
				}
		}
}

class TsvBasedEtcFileMetadataStore(
	utcOffsets: Map[StationId, Int], fileInfo: Map[EtcFileMetaKey, EtcFileMeta], tcIds: Map[StationId, Int]
) extends EtcFileMetadataStore{

	override def lookupFile(key: EtcFileMetaKey): Option[EtcFileMeta] = fileInfo.get(key)

	override def getUtcOffset(station: StationId): Option[Int] = utcOffsets.get(station)
		.orElse(EtcFileMetadataStore.fallbackUtcOffset(station))

	override def stationTcId(station: StationId): Option[Int] = tcIds.get(station)

}
