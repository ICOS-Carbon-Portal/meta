package se.lu.nateko.ingester.model.dto;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class ReferencesDto {
	private Optional<List<String>> keywords;
	private Optional<URI> license;
	private Optional<Instant> moratorium;
	private Optional<Boolean> duplicateFileNameAllowed;
	private Optional<Boolean> autoDeprecateSameFileNameObjects;
	private Optional<Boolean> partialUpload;

	public ReferencesDto(
		Optional<List<String>> keywords,
		Optional<URI> license,
		Optional<Instant> moratorium,
		Optional<Boolean> duplicateFileNameAllowed,
		Optional<Boolean> autoDeprecateSameFileNameObjects,
		Optional<Boolean> partialUpload
	) {
		this.keywords = keywords;
		this.license = license;
		this.moratorium = moratorium;
		this.duplicateFileNameAllowed = duplicateFileNameAllowed;
		this.autoDeprecateSameFileNameObjects = autoDeprecateSameFileNameObjects;
		this.partialUpload = partialUpload;
	}

	public Optional<List<String>> getKeywords() {
		return keywords;
	}

	public Optional<URI> getLicense() {
		return license;
	}

	public Optional<Instant> getMoratorium() {
		return moratorium;
	}

	public Optional<Boolean> getDuplicateFileNameAllowed() {
		return duplicateFileNameAllowed;
	}

	public Optional<Boolean> getAutoDeprecateSameFileNameObjects() {
		return autoDeprecateSameFileNameObjects;
	}

	public Optional<Boolean> getPartialUpload() {
		return partialUpload;
	}
}
