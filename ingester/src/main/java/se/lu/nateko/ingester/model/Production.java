package se.lu.nateko.ingester.model;

import java.net.URI;
import java.util.Date;
import java.util.List;

public record Production(
	URI creator,
	List<String> contributors,
	URI hostOrganisation,
	String comment,
	Date creationDate,
	List<String> sources,
	String documentation
) {}
