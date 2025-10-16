package se.lu.nateko.ingester.model.dto;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class SpatioTemporalDto implements SpecificInfoDto {
	private String title;
	private Optional<String> description;
	private String spatial;
	private TemporalCoverage temporal;
	private DataProductionDto production;
	private Optional<URI> forStation;
	private Optional<Float> samplingHeight;
	private Optional<URI> customLandingPage;
	private List<String> variables;

	public SpatioTemporalDto(
		String title,
		Optional<String> description,
		String spatial,
		TemporalCoverage temporal,
		DataProductionDto production,
		Optional<URI> forStation,
		Optional<Float> samplingHeight,
		Optional<URI> customLandingPage,
		List<String> variables
	) {
		this.title = title;
		this.description = description;
		this.spatial = spatial;
		this.temporal = temporal;
		this.production = production;
		this.forStation = forStation;
		this.samplingHeight = samplingHeight;
		this.customLandingPage = customLandingPage;
		this.variables = variables;
	}

	public String getTitle() {
		return title;
	}

	public Optional<String> getDescription() {
		return description;
	}

	public String getSpatial() {
		return spatial;
	}

	public TemporalCoverage getTemporal() {
		return temporal;
	}

	public DataProductionDto getProduction() {
		return production;
	}

	public Optional<URI> getForStation() {
		return forStation;
	}

	public Optional<Float> getSamplingHeight() {
		return samplingHeight;
	}

	public Optional<URI> getCustomLandingPage() {
		return customLandingPage;
	}

	public List<String> getVariables() {
		return variables;
	}
}
