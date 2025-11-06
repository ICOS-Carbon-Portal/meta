package se.lu.nateko.ingester.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import se.lu.nateko.ingester.model.dto.DataObjectDto;
import se.lu.nateko.ingester.model.dto.TimeInterval;
import se.lu.nateko.ingester.model.entity.StationSpecificDataObjectEntity;
import se.lu.nateko.ingester.repository.IngestRepository;

@Service
public class IngestService {
	private IngestRepository ingestRepository;

	public IngestService(IngestRepository ingestRepository) {
		this.ingestRepository = ingestRepository;
	}

	public void saveStationSpecificDataObject(DataObjectDto dataObject) {
		ingestRepository.save(toStationTimeSeriesEntity(dataObject));
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
}
