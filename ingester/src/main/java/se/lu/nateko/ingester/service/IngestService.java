package se.lu.nateko.ingester.service;

import org.springframework.stereotype.Service;

import se.lu.nateko.ingester.model.dto.DataObjectDto;
import se.lu.nateko.ingester.model.dto.SpatioTemporalDto;
import se.lu.nateko.ingester.model.dto.StationTimeSeriesDto;
import se.lu.nateko.ingester.model.entity.SpatioTemporalDataEntity;
import se.lu.nateko.ingester.model.entity.StationSpecificDataObjectEntity;
import se.lu.nateko.ingester.repository.IngestRepository;

@Service
public class IngestService {
	private IngestRepository ingestRepository;

	public void saveStationSpecificDataObject(DataObjectDto dataObject) {
		if (dataObject.getSpecificInfo() instanceof StationTimeSeriesDto stationTimeSeriesDto) {
			ingestRepository.saveStationTimeSeriesData(
				toStationTimeSeriesEntity(dataObject, stationTimeSeriesDto)
			);
		} else if (dataObject.getSpecificInfo() instanceof SpatioTemporalDto spatioTemporalDto) {
			ingestRepository.saveSpatioTemporalData(
				toSpatioTemporalEntity(dataObject, spatioTemporalDto)
			);
		}
	}

	public StationSpecificDataObjectEntity toStationTimeSeriesEntity(
		DataObjectDto dataObject, StationTimeSeriesDto specificInfo
	) {
		return new StationSpecificDataObjectEntity(
			Integer.valueOf(dataObject.hashCode()),
			dataObject.getSubmitterId(),
			dataObject.getHashSum(),
			dataObject.getFileName(),
			specificInfo.getStation().toString(),
			specificInfo.getAcquisitionInterval().get().getStart(),
			specificInfo.getAcquisitionInterval().get().getStop(),
			specificInfo.getSamplingHeight().get()
		);
	}

	public SpatioTemporalDataEntity toSpatioTemporalEntity(
		DataObjectDto dataObject, SpatioTemporalDto specificInfo
	) {
		return new SpatioTemporalDataEntity(
			Integer.valueOf(dataObject.hashCode())
		);
	}
}
