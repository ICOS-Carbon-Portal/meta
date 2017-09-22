package se.lu.nateko.cp.meta.services.upload.etc

import se.lu.nateko.cp.meta.core.etcupload.DataType
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.ingestion.badm.BadmEntry

case class EtcFileMeta(dtype: DataType.Value, isBinary: Boolean)
case class EtcLoggerMeta(serial: String, model: String)

trait EtcFileMetadataStore {

	def lookupFile(station: StationId, loggerId: Int, fileId: Int): Option[EtcFileMeta]

	def lookupLogger(station: StationId, loggerId: Int): Option[EtcLoggerMeta]

	def getUtcOffset(station: StationId): Int
}

object EtcFileMetadataStore{
	def apply(entries: Seq[BadmEntry]): EtcFileMetadataStore = ???
}
