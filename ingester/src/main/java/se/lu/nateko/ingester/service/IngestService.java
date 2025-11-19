package se.lu.nateko.ingester.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import se.lu.nateko.ingester.model.dto.DataObjectDto;
import se.lu.nateko.ingester.model.dto.TimeInterval;
import se.lu.nateko.ingester.model.dto.etc.EtcUploadMetadataDto;
import se.lu.nateko.ingester.model.entity.EtcUploadMetadataEntity;
import se.lu.nateko.ingester.model.entity.StationSpecificDataObjectEntity;
import se.lu.nateko.ingester.repository.IngestRepository;
import se.lu.nateko.ingester.repository.etc.EtcUploadMetadataRepository;

@Service
public class IngestService {
	private IngestRepository ingestRepository;
	private EtcUploadMetadataRepository etcUploadMetadataRepository;

	public IngestService(IngestRepository ingestRepository, EtcUploadMetadataRepository etcUploadMetadataRepository) {
		this.ingestRepository = ingestRepository;
		this.etcUploadMetadataRepository = etcUploadMetadataRepository;
	}

	public void saveStationSpecificDataObject(DataObjectDto dataObject) {
		ingestRepository.save(toStationTimeSeriesEntity(dataObject));
	}

	public void saveEtcUploadMetadata(EtcUploadMetadataDto dto) {
		etcUploadMetadataRepository.save(toEtcUploadMetadataEntity(dto));
	}

	public StationSpecificDataObjectEntity toStationTimeSeriesEntity(
		DataObjectDto dataObject
	) {
		Optional<TimeInterval> interval = dataObject.getSpecificInfo().getAcquisitionInterval();
		Instant startDate = null;
		Instant endDate = null;
		if (interval.isPresent()) {
			startDate = interval.get().getStart();
			endDate = interval.get().getStop();
		}
		return new StationSpecificDataObjectEntity(
			Integer.valueOf(dataObject.hashCode()),
			dataObject.getSubmitterId(),
			dataObject.getHashSum(),
			dataObject.getFileName(),
			dataObject.getSpecificInfo().getStation().toString(),
			startDate,
			endDate,
			dataObject.getSpecificInfo().getSamplingHeight().orElse(null)
		);
	}

	public EtcUploadMetadataEntity toEtcUploadMetadataEntity(EtcUploadMetadataDto dto) {
		return new EtcUploadMetadataEntity(
			dto.getHashSum(),
			dto.getFileName(),
			dto.getStation().getId(),
			dto.getLogger(),
			dto.getDataType().name(),
			dto.getFileId(),
			dto.getAcquisitionStart(),
			dto.getAcquisitionStop()
		);
	}
}
