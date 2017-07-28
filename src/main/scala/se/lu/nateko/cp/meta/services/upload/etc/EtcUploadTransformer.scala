package se.lu.nateko.cp.meta.services.upload.etc

import se.lu.nateko.cp.meta.core.etcupload.EtcUploadMetadata
import se.lu.nateko.cp.meta.core.etcupload.DataType
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.EtcUploadConfig
import se.lu.nateko.cp.meta.StationDataMetadata
import se.lu.nateko.cp.meta.utils.rdf4j._

class EtcUploadTransformer(config: EtcUploadConfig) {

	def transform(meta: EtcUploadMetadata, vocab: CpVocab) = {

		val objSpec = vocab.getObjectSpecification(getObjSpecUrlSegment(meta.dataType))

		UploadMetadataDto(
			hashSum = meta.hashSum,
			submitterId = "dummy", //will not be used
			objectSpecification = objSpec.toJava,
			fileName = meta.fileName,
			specificInfo = Right(
				StationDataMetadata(
					station = vocab.getEcosystemStation(meta.station).toJava,
					//TODO Add instrument info
					instrument = None,
					//TODO Add acquisition interval info
					acquisitionInterval = None,
					nRows = None,
					production = None
				)
			),
			isNextVersionOf = None
		)
	}

	private def getObjSpecUrlSegment(dtype: DataType.Value): String = dtype match {
		case DataType.BM => config.bioMeteoObjSpecId
		case DataType.EC => config.eddyCovarObjSpecId
		case DataType.ST => config.storageObjSpecId
	}
}
