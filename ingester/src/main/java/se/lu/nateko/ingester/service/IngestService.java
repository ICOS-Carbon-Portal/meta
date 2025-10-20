package se.lu.nateko.ingester.service;

import org.springframework.stereotype.Service;

import se.lu.nateko.ingester.model.dto.DataObjectDto;
import se.lu.nateko.ingester.model.dto.SpatioTemporalDto;
import se.lu.nateko.ingester.model.dto.StationTimeSeriesDto;
import se.lu.nateko.ingester.model.entity.SpatioTemporalDataEntity;
import se.lu.nateko.ingester.model.entity.StationSpecificDataObjectEntity;
import se.lu.nateko.ingester.repository.IngestRepository;
import se.lu.nateko.ingester.repository.SpatioTemporalDataRepository;

@Service
public class IngestService {
	private IngestRepository ingestRepository;
	private SpatioTemporalDataRepository spatioTemporalDataRepository;

	public IngestService(IngestRepository ingestRepository, SpatioTemporalDataRepository spatioTemporalDataRepository) {
		this.ingestRepository = ingestRepository;
		this.spatioTemporalDataRepository = spatioTemporalDataRepository;
	}

	public void saveStationSpecificDataObject(DataObjectDto dataObject) {
		if (dataObject.getSpecificInfo() instanceof StationTimeSeriesDto stationTimeSeriesDto) {
			ingestRepository.save(
				toStationTimeSeriesEntity(dataObject, stationTimeSeriesDto)
			);
		} else if (dataObject.getSpecificInfo() instanceof SpatioTemporalDto spatioTemporalDto) {
			spatioTemporalDataRepository.save(
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
