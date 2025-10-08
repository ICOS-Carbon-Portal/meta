package se.lu.nateko.ingester.model;

import java.net.URI;
import java.util.Date;
import java.util.List;

public record References(
	List<String> keywords,
	URI licence,
	Date moratorium,
	Boolean duplicateFilenameAllowed
) {}
