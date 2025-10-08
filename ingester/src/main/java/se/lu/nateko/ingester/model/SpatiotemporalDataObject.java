package se.lu.nateko.ingester.model;

import java.net.URI;
import java.util.List;

public record SpatiotemporalDataObject(
	String title,
	String description,
	URI spatial,
	Temporal temporal,
	Production production,
	String forStation,
	Double samplingHeight,
	String customLandingPage,
	List<String> variables
) {}
