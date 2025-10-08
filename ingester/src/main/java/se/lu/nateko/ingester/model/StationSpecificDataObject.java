package se.lu.nateko.ingester.model;

import java.net.URI;

public record StationSpecificDataObject(
	String submitterId,
	String hashSum,
	String fileName,
	SpecificInfo specificInfo,
	URI objectSpecification,
	String isNextVersionOf,
	String preExistingDoi,
	References references
) {}
