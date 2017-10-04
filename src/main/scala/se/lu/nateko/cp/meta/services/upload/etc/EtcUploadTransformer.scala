package se.lu.nateko.cp.meta.services.upload.etc

import java.time.LocalDateTime
import java.time.ZoneOffset

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.ActorSystem
import akka.stream.Materializer
import se.lu.nateko.cp.meta.EtcUploadConfig
import se.lu.nateko.cp.meta.StationDataMetadata
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.core.etcupload.DataType
import se.lu.nateko.cp.meta.core.etcupload.EtcUploadMetadata
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.services.MetadataException

class EtcUploadTransformer(config: EtcUploadConfig)(implicit system: ActorSystem, m: Materializer) {

	val etcMeta: EtcFileMetadataStore = new EtcFileMetadataProvider

	def transform(meta: EtcUploadMetadata, vocab: CpVocab): Try[UploadMetadataDto] = {

		val objSpec = vocab.getObjectSpecification(getObjSpecUrlSegment(meta.dataType))

		def getCpUploadMeta(utcOffset: Int) = UploadMetadataDto(
			hashSum = meta.hashSum,
			submitterId = "dummy", //will not be used
			objectSpecification = objSpec.toJava,
			fileName = meta.fileName,
			specificInfo = Right(
				StationDataMetadata(
					station = vocab.getEcosystemStation(meta.station).toJava,
					instrument = Some(vocab.getEtcInstrument(meta.station, meta.logger).toJava),
					acquisitionInterval = Some(getAcquisitionInterval(utcOffset)),
					nRows = None,
					production = None
				)
			),
			isNextVersionOf = None
		)

		def getAcquisitionInterval(offset: Int) = {
			def getInstant(dt: LocalDateTime) = dt.atOffset(ZoneOffset.ofHours(offset)).toInstant
			TimeInterval(getInstant(meta.acquisitionStart), getInstant(meta.acquisitionStop))
		}

		etcMeta.getUtcOffset(meta.station)
			.map(Success.apply)
			.getOrElse(Failure(new MetadataException(
				s"UTC offset info for station ${meta.station.id} not found in ETC metadata on Carbon Portal"
			)))
			.map(getCpUploadMeta)
	}

	private def getObjSpecUrlSegment(dtype: DataType.Value): String = dtype match {
		case DataType.BM => config.bioMeteoObjSpecId
		case DataType.EC => config.eddyCovarObjSpecId
		case DataType.ST => config.storageObjSpecId
	}
}
