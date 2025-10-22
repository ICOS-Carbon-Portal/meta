package se.lu.nateko.ingester.service;

import org.springframework.stereotype.Service;

import se.lu.nateko.ingester.model.dto.DataObjectDto;
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
		return new StationSpecificDataObjectEntity(
			Integer.valueOf(dataObject.hashCode()),
			dataObject.getSubmitterId(),
			dataObject.getHashSum(),
			dataObject.getFileName(),
			dataObject.getSpecificInfo().getStation().toString(),
			dataObject.getSpecificInfo().getAcquisitionInterval().orElse(null).getStart(),
			dataObject.getSpecificInfo().getAcquisitionInterval().orElse(null).getStop(),
			dataObject.getSpecificInfo().getSamplingHeight().orElse(null)
		);
	}
}
