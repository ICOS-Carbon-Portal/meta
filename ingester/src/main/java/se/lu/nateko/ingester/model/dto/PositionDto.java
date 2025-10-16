package se.lu.nateko.ingester.model.dto;

import java.net.URI;
import java.util.Optional;

public class PositionDto {
	private Double latitude;
	private Double longitude;
	private Optional<Float> altitude;
	private Optional<String> label;
	private Optional<URI> uri;

	public PositionDto(
		Double latitude,
		Double longitude,
		Optional<Float> altitude,
		Optional<String> label,
		Optional<URI> uri
	) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.altitude = altitude;
		this.label = label;
		this.uri = uri;
	}

	public Double getLatitude() {
		return latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public Optional<Float> getAltitude() {
		return altitude;
	}

	public Optional<String> getLabel() {
		return label;
	}

	public Optional<URI> getUri() {
		return uri;
	}
}
