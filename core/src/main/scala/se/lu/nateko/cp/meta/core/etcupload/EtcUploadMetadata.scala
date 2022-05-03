package se.lu.nateko.cp.meta.core.etcupload

import java.time.LocalDateTime
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

//TODO Move EtcFileMetaKey in here, use it for station/looger/dataType/fileId
case class EtcUploadMetadata(
	hashSum: Sha256Sum,
	fileName: String,
	station: StationId,
	logger: Int,
	dataType: DataType,
	fileId: Int,
	acquisitionStart: LocalDateTime,
	acquisitionStop: LocalDateTime
)

enum DataType:
	case EC, BM, ST, SAHEAT
