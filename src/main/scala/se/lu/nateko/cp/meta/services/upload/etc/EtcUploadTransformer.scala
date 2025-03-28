package se.lu.nateko.cp.meta.services.upload.etc

import akka.actor.ActorSystem
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.api.{SparqlQuery, SparqlRunner, UriId}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.core.etcupload.{DataType, EtcUploadMetadata, StationId}
import se.lu.nateko.cp.meta.services.{CpVocab, MetadataException}
import se.lu.nateko.cp.meta.utils.*
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.{DataObjectDto, EtcConfig, StationTimeSeriesDto}

import java.time.{LocalDateTime, ZoneOffset}
import scala.util.{Success, Try}

class EtcUploadTransformer(sparqler: SparqlRunner, config: EtcConfig, vocab: CpVocab)(using ActorSystem) {

	val etcMeta: EtcFileMetadataStore = new EtcFileMetadataProvider(config, vocab)
	private given Envri = Envri.ICOS

	def transform(meta: EtcUploadMetadata): Try[DataObjectDto] = {

		def getAcquisitionInterval(offset: Int) = {
			def getInstant(dt: LocalDateTime) = dt.atOffset(ZoneOffset.ofHours(offset)).toInstant
			TimeInterval(getInstant(meta.acquisitionStart), getInstant(meta.acquisitionStop))
		}

		for(
			utcOffset <- getUtcOffset(meta.station);
			tcIntId <- getTcInternalId(meta.station);
			fileMeta <- getFileMeta(meta);
			specUriSegment = getObjSpecUrlSegment(fileMeta);
			objSpec = vocab.getObjectSpecification(specUriSegment)
		) yield DataObjectDto(
			hashSum = meta.hashSum,
			submitterId = "dummy", //will not be used
			objectSpecification = objSpec.toJava,
			fileName = meta.fileName,
			specificInfo = Right(
				StationTimeSeriesDto(
					station = vocab.getEcosystemStation(meta.station).toJava,
					site = None,
					instrument = Some(Left(vocab.getEtcInstrument(tcIntId, meta.logger).toJava)),
					samplingPoint = None,
					samplingHeight = None,
					acquisitionInterval = Some(getAcquisitionInterval(utcOffset)),
					nRows = None,
					production = None,
					spatial = None
				)
			),
			isNextVersionOf = getPrevVersion(meta.fileName, meta.hashSum).map(Left(_)),
			preExistingDoi = None,
			references = None
		)
	}

	private def getObjSpecUrlSegment(meta: EtcFileMeta): UriId = {

		val baseSegment = meta.dtype match {
			case DataType.BM => config.bioMeteoObjSpecId
			case DataType.EC => config.eddyCovarObjSpecId
			case DataType.ST => config.storageObjSpecId
			case DataType.SAHEAT => config.saheatObjSpecId
			case DataType.PHEN => config.phenocamObjSpecId
		}

		val binSuff = meta.dtype match {
			case DataType.SAHEAT | DataType.PHEN => ""
			case _ => if(meta.isBinary) "Bin" else "Csv"
		}

		UriId(baseSegment + binSuff)
	}

	private def getTcInternalId(station: StationId): Try[Int] = etcMeta
		.stationTcId(station)
		.toTry(new MetadataException(
			s"Could not look up internal TC id for station ${station.id}"
		))

	private def getUtcOffset(station: StationId): Try[Int] = etcMeta
		.getUtcOffset(station)
		.toTry(new MetadataException(
			s"UTC offset info for station ${station.id} not found in ETC metadata on Carbon Portal"
		))

	private def getFileMeta(meta: EtcUploadMetadata): Try[EtcFileMeta] = meta.dataType match {
		case DataType.SAHEAT | DataType.PHEN =>
			Success(EtcFileMeta(dtype = meta.dataType, isBinary = false))
		case _ =>
			etcMeta
				.lookupFile(EtcFileMetaKey(meta.station, meta.logger, meta.fileId, meta.dataType))
				.toTry(new MetadataException(
					s"Could not find ETC file metadata for $meta on Carbon Portal"
				))
	}

	private def getPrevVersion(fileName: String, thisHash: Sha256Sum): Option[Sha256Sum] = {
		val query = s"""prefix prov: <http://www.w3.org/ns/prov#>
			|prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
			|select distinct ?dobj where{
			|	?dobj cpmeta:hasName "$fileName" .
			|	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submEnd .
			|}
			|order by desc(?submEnd)
			|limit 2""".stripMargin //limit 2 is to include potentially itself and the latest other upload of this filename
		sparqler.evaluateTupleQuery(SparqlQuery(query))
			.map{bs =>
				val hashSegm = bs.getValue("dobj").stringValue.split("/").last
				Sha256Sum.fromBase64Url(hashSegm).toOption
			}
			.flatten
			.filter(_ != thisHash)
			.toIndexedSeq //to consume the iterator
			.headOption
	}
}
