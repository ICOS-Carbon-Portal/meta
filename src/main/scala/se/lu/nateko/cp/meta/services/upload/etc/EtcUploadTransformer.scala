package se.lu.nateko.cp.meta.services.upload.etc

import java.time.LocalDateTime
import java.time.ZoneOffset

import scala.util.Try

import akka.actor.ActorSystem
import akka.stream.Materializer
import se.lu.nateko.cp.meta.EtcUploadConfig
import se.lu.nateko.cp.meta.StationDataMetadata
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.core.etcupload.DataType
import se.lu.nateko.cp.meta.core.etcupload.EtcUploadMetadata
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.utils._
import se.lu.nateko.cp.meta.utils.rdf4j._

class EtcUploadTransformer(config: EtcUploadConfig)(implicit system: ActorSystem, m: Materializer) {

	val etcMeta: EtcFileMetadataStore = new EtcFileMetadataProvider

	def transform(meta: EtcUploadMetadata, vocab: CpVocab): Try[UploadMetadataDto] = {

		def getAcquisitionInterval(offset: Int) = {
			def getInstant(dt: LocalDateTime) = dt.atOffset(ZoneOffset.ofHours(offset)).toInstant
			TimeInterval(getInstant(meta.acquisitionStart), getInstant(meta.acquisitionStop))
		}

		for(
			utcOffset <- getUtcOffset(meta.station);
			fileMeta <- getFileMeta(meta);
			specUriSegment = getObjSpecUrlSegment(meta.fileName, fileMeta);
			objSpec = vocab.getObjectSpecification(specUriSegment)
		) yield UploadMetadataDto(
			hashSum = meta.hashSum,
			submitterId = "dummy", //will not be used
			objectSpecification = objSpec.toJava,
			fileName = meta.fileName,
			specificInfo = Right(
				StationDataMetadata(
					station = vocab.getEcosystemStation(meta.station).toJava,
					instrument = Some(vocab.getEtcInstrument(meta.station, meta.logger).toJava),
					samplingHeight = None,
					acquisitionInterval = Some(getAcquisitionInterval(utcOffset)),
					nRows = None,
					production = None
				)
			),
			isNextVersionOf = None
		)
	}

	private def getObjSpecUrlSegment(fileName: String, meta: EtcFileMeta): String = {

		val baseSegment = meta.dtype match {
			case DataType.BM => config.bioMeteoObjSpecId
			case DataType.EC => config.eddyCovarObjSpecId
			case DataType.ST => config.storageObjSpecId
		}

		val binSuff = if(meta.isBinary) "Bin" else "Csv"
		val zipSuff = if(fileName.endsWith("zip")) "Zip" else ""

		baseSegment + binSuff + zipSuff
	}

	private def getUtcOffset(station: StationId): Try[Int] = etcMeta
		.getUtcOffset(station)
		.toTry(new MetadataException(
			s"UTC offset info for station ${station.id} not found in ETC metadata on Carbon Portal"
		))

	private def getFileMeta(meta: EtcUploadMetadata): Try[EtcFileMeta] = etcMeta
		.lookupFile(meta.station, meta.logger, meta.fileId, meta.dataType)
		.toTry(new MetadataException(
			s"Could not find ETC file metadata for $meta on Carbon Portal"
		))
}
