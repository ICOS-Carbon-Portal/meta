package se.lu.nateko.ingester.model.dto;

import java.util.Optional;

public class TemporalCoverage {
	private TimeInterval interval;
	private Optional<String> resolution;

	public TemporalCoverage(TimeInterval interval, Optional<String> resolution) {
		this.interval = interval;
		this.resolution = resolution;
	}

	public TimeInterval getInterval() {
		return interval;
	}

	public Optional<String> getResolution() {
		return resolution;
	}
}
