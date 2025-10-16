package se.lu.nateko.ingester.model.dto;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class DocObjectDto {
	private String hashSum;
	private String submitterId;
	private String fileName;
	private Optional<String> title;
	private Optional<String> description;
	private List<URI> authors;
	private List<String> isNextVersionOf;
	private Optional<DoiDto> preExistingDoi;
	private Optional<ReferencesDto> references;

	public DocObjectDto(
		String hashSum,
		String submitterId,
		String fileName,
		Optional<String> title,
		Optional<String> description,
		List<URI> authors,
		List<String> isNextVersionOf,
		Optional<DoiDto> preExistingDoi,
		Optional<ReferencesDto> references
	) {
		this.hashSum = hashSum;
		this.submitterId = submitterId;
		this.fileName = fileName;
		this.title = title;
		this.description = description;
		this.authors = authors;
		this.isNextVersionOf = isNextVersionOf;
		this.preExistingDoi = preExistingDoi;
		this.references = references;
	}

	public String getHashSum() {
		return hashSum;
	}

	public String getSubmitterId() {
		return submitterId;
	}

	public String getFileName() {
		return fileName;
	}

	public Optional<String> getTitle() {
		return title;
	}

	public Optional<String> getDescription() {
		return description;
	}

	public List<URI> getAuthors() {
		return authors;
	}

	public List<String> getIsNextVersionOf() {
		return isNextVersionOf;
	}

	public Optional<DoiDto> getPreExistingDoi() {
		return preExistingDoi;
	}

	public Optional<ReferencesDto> getReferences() {
		return references;
	}
}
