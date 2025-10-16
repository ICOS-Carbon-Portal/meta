package se.lu.nateko.ingester.model.dto;

import java.util.Optional;

public class SpatioTemporalDto implements SpecificInfoDto {
	private String title;
	private Optional<String> description;
	private GeoCoverage spatial;

}
