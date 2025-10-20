package se.lu.nateko.ingester.model.dto;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class StationTimeSeriesDto implements SpecificInfo {
	private URI station;
	private Optional<URI> site;
	private List<URI> instrument;
	private Optional<PositionDto> position;
	private Optional<Float> samplingHeight;
	private Optional<TimeInterval> acquisitionInterval;
	private Optional<Integer> nRows;
	private Optional<DataProductionDto> production;
	private Optional<String> spatial;

	public StationTimeSeriesDto(
		URI station,
		Optional<URI> site,
		List<URI> instrument,
		Optional<PositionDto> position,
		Optional<Float> samplingHeight,
		Optional<TimeInterval> acquisitionInterval,
		Optional<Integer> nRows,
		Optional<DataProductionDto> production,
		Optional<String> spatial
	) {
		this.station = station;
		this.site = site;
		this.instrument = instrument;
		this.position = position;
		this.samplingHeight = samplingHeight;
		this.acquisitionInterval = acquisitionInterval;
		this.nRows = nRows;
		this.production = production;
		this.spatial = spatial;
	}

	public URI getStation() {
		return station;
	}

	public Optional<URI> getSite() {
		return site;
	}

	public List<URI> getInstrument() {
		return instrument;
	}

	public Optional<PositionDto> getPosition() {
		return position;
	}

	public Optional<Float> getSamplingHeight() {
		return samplingHeight;
	}

	public Optional<TimeInterval> getAcquisitionInterval() {
		return acquisitionInterval;
	}

	public Optional<Integer> getnRows() {
		return nRows;
	}

	public Optional<DataProductionDto> getProduction() {
		return production;
	}

	public Optional<String> getSpatial() {
		return spatial;
	}
}
