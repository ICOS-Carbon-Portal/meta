package se.lu.nateko.cp.meta.services.upload.etc

import se.lu.nateko.cp.meta.core.etcupload.DataType
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.ingestion.badm.{Badm, BadmEntry, BadmValue}

case class EtcFileMeta(dtype: DataType, isBinary: Boolean)
case class EtcFileMetaKey(station: StationId, loggerId: Int, fileId: Int, dataType: DataType)
case class EtcLoggerMeta(serial: String, model: String)

private class EtcStation(val stationId: StationId, val utcOffset: Int)

trait EtcFileMetadataStore {

	def lookupFile(key: EtcFileMetaKey): Option[EtcFileMeta]

	def getUtcOffset(station: StationId): Option[Int]

	def stationTcId(station: StationId): Option[Int]
}

object EtcFileMetadataStore {

	def fallbackUtcOffset(station: StationId): Option[Int] = station.id.take(2) match {
		case "BE" | "CH" | "CZ" | "DE" | "DK" | "ES" | "FR" | "HU" | "IT" | "NL" | "NO" | "PL" | "SE" => Some(1)
		case "GB" | "PT" | "IE" => Some(0)
		case "FI" | "GR" | "IL" => Some(2)
		case "RU" => Some(3)
		case _ => None
	}
}
