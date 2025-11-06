package se.lu.nateko.ingester.model.dto;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import se.lu.nateko.ingester.deserialize.ListUriDeserializer;

public class SpecificInfoDto {
	private Optional<URI> station;
	private Optional<URI> site;
	@JsonDeserialize(using = ListUriDeserializer.class)
	private List<URI> instrument;
	private Optional<PositionDto> position;
	private Optional<Float> samplingHeight;
	private Optional<TimeInterval> acquisitionInterval;
	private Optional<Integer> nRows;
	private Optional<DataProductionDto> production;
	private Optional<JsonNode> spatial;
	private String title;
	private Optional<String> description;
	private TemporalCoverage temporal;
	private Optional<URI> forStation;
	private Optional<URI> customLandingPage;
	private List<String> variables;

	public SpecificInfoDto(
		Optional<URI> station,
		Optional<URI> site,
		List<URI> instrument,
		Optional<PositionDto> position,
		Optional<Float> samplingHeight,
		Optional<TimeInterval> acquisitionInterval,
		Optional<Integer> nRows,
		Optional<DataProductionDto> production,
		Optional<JsonNode> spatial,
		String title,
		Optional<String> description,
		TemporalCoverage temporal,
		Optional<URI> forStation,
		Optional<URI> customLandingPage,
		List<String> variables
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
		this.title = title;
		this.description = description;
		this.temporal = temporal;
		this.forStation = forStation;
		this.customLandingPage = customLandingPage;
		this.variables = variables;
	}

	public Optional<URI> getStation() {
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

	public Optional<JsonNode> getSpatial() {
		return spatial;
	}

	public String getTitle() {
		return title;
	}

	public Optional<String> getDescription() {
		return description;
	}

	public TemporalCoverage getTemporal() {
		return temporal;
	}

	public Optional<URI> getForStation() {
		return forStation;
	}

	public Optional<URI> getCustomLandingPage() {
		return customLandingPage;
	}

	public List<String> getVariables() {
		return variables;
	}
}
