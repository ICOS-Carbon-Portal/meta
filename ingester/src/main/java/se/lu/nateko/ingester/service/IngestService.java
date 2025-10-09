package se.lu.nateko.ingester.service;

import org.springframework.stereotype.Service;

import se.lu.nateko.ingester.model.StationSpecificDataObject;
import se.lu.nateko.ingester.model.entity.StationSpecificDataObjectEntity;
import se.lu.nateko.ingester.repository.IngestRepository;

@Service
public class IngestService {
	private IngestRepository ingestRepository;

	public void saveStationSpecificDataObject(StationSpecificDataObject stationSpecificDataObject) {
		ingestRepository.saveStationSpecificDataObject(
			toSpecificStationDataEntity(stationSpecificDataObject)
		);
	}

	public StationSpecificDataObjectEntity toSpecificStationDataEntity(StationSpecificDataObject dataObject) {
		return new StationSpecificDataObjectEntity(
			Integer.valueOf(dataObject.hashCode()),
			dataObject.submitterId(),
			dataObject.hashSum(),
			dataObject.fileName(),
			dataObject.specificInfo().station().toString(),
			dataObject.specificInfo().acquisitionInterval().start(),
			dataObject.specificInfo().acquisitionInterval().stop(),
			dataObject.specificInfo().samplingHeight()
		);
	}
}
