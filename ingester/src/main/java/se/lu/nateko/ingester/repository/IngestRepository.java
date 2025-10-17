package se.lu.nateko.ingester.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import se.lu.nateko.ingester.model.entity.SpatioTemporalDataEntity;
import se.lu.nateko.ingester.model.entity.StationSpecificDataObjectEntity;

@Repository
public interface IngestRepository {
	public List<StationSpecificDataObjectEntity> fetchStationSpecificDataObjects();

	public void saveStationTimeSeriesData(StationSpecificDataObjectEntity stationSpecificDataObjectEntity);

	public void saveSpatioTemporalData(SpatioTemporalDataEntity spatioTemporalDataEntity);
}
