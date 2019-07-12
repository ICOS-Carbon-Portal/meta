package se.lu.nateko.cp.meta.services.upload.etc

import se.lu.nateko.cp.meta.core.etcupload.DataType
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.ingestion.badm.{Badm, BadmEntry, BadmValue}

case class EtcFileMeta(dtype: DataType.Value, isBinary: Boolean)
case class EtcLoggerMeta(serial: String, model: String)

private class EtcStation(val stationId: StationId, val utcOffset: Int)

class EtcFileMetadataStoreImpl(entries: Seq[BadmEntry]) extends EtcFileMetadataStore {
	private type LoggerKey = (StationId, Int)
	private type FileKey = (StationId, Int, Int, DataType.Value)

	private val stations: Map[StationId, EtcStation] = {
		val tuples = for {
			entry <- entries
			if entry.variable == "GRP_UTC_OFFSET"
			id <- entry.stationId
			utcOffset <- entry.values.collectFirst{
				case BadmValue("UTC_OFFSET", Badm.Numeric(utc)) => utc.intValue
			}
		} yield id -> new EtcStation(id, utcOffset)

		tuples.toMap
	}

	private val loggers: Map[LoggerKey, EtcLoggerMeta] = {
		val seq = for {
			logger <- entries
			if logger.variable == "GRP_LOGGER"
			stationId <- logger.stationId
			loggerId <- logger.values.collectFirst{
				case BadmValue("LOGGER_ID", Badm.Numeric(id)) => id.intValue
			}
			loggerKey = (stationId, loggerId)
			loggerSN <- logger.values.collectFirst{
				case BadmValue("LOGGER_SN", sn) => sn
			}
			loggerModel <- logger.values.collectFirst{
				case BadmValue("LOGGER_MODEL", sn) => sn
			}
		} yield (loggerKey, EtcLoggerMeta(loggerSN, loggerModel))
		seq.toMap
	}

	private val files: Map[FileKey, EtcFileMeta] = {
		val seq = for {
			entry <- entries
			if entry.variable == "GRP_FILE"
			file = entry
			stationId <- file.stationId
			loggerId <- file.values.collectFirst {
				case BadmValue("FILE_LOGGER_ID", Badm.Numeric(id)) => id.intValue
			}
			fileId <- file.values.collectFirst {
				case BadmValue("FILE_ID", Badm.Numeric(id)) => id.intValue
			}
			dtype <- file.values.collectFirst {
				case BadmValue("FILE_TYPE", ft) =>
					DataType.values.find(_.toString == ft.trim)
			}.flatten
			fileKey = (stationId, loggerId, fileId, dtype)
			isBinary <- file.values.collectFirst {
				case BadmValue("FILE_FORMAT", ff) => ff == "Binary"
			}
		} yield (fileKey, EtcFileMeta(dtype, isBinary))

		seq.toMap
	}

	def lookupLogger(station: StationId, loggerId: Int) =	loggers.get((station, loggerId))

	def getUtcOffset(station: StationId) = stations.get(station)
		.map(_.utcOffset)
		.orElse(EtcFileMetadataStore.fallbackUtcOffset(station))

	def lookupFile(station: StationId, loggerId: Int, fileId: Int, dtype: DataType.Value) = files.get((station, loggerId, fileId, dtype))
}

trait EtcFileMetadataStore {

	def lookupFile(station: StationId, loggerId: Int, fileId: Int, dataType: DataType.Value): Option[EtcFileMeta]

	def lookupLogger(station: StationId, loggerId: Int): Option[EtcLoggerMeta]

	def getUtcOffset(station: StationId): Option[Int]
}

object EtcFileMetadataStore {
	def apply(entries: Seq[BadmEntry]): EtcFileMetadataStore = {
		new EtcFileMetadataStoreImpl(entries)
	}

	def fallbackUtcOffset(station: StationId): Option[Int] = station.id.take(2) match {
		case "BE" | "CH" | "CZ" | "DE" | "DK" | "ES" | "FR" | "IT" | "NL" | "SE" => Some(1)
		case "FI" => Some(2)
		case "RU" => Some(3)
		case _ => None
	}
}
